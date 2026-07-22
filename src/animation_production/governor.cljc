(ns animation-production.governor
  "AnimationProductionGovernor — the independent rights/consent/traceability
  layer for the JSIC 4113 独立アニメーション制作 (independent animation
  production studio) actor. Wired as its own `:govern` node in
  `animation-production.actor`'s StateGraph, downstream of `:advise` — the
  Advisor has no notion of production provenance or talent consent/rights
  clearance, so this MUST be a separate system able to reject a proposal
  (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  The cut sanity check is NOT bespoke: `build-cut-plan` walks the actual
  `anime.production/layer-specs` (the cut→layer vocabulary of the
  kotoba-lang `anime` craft lib, ADR-2607023000) against a cut's `:layers`
  map to see which layers a cut has actually produced, and
  `anime.production/derive-cut-priority` to see whether any pipeline stage
  is flagged `\"retake\"` — an assembly whose cut plan has zero completed
  layers, or whose derived priority is `\"retake\"`, goes to a human instead
  of silently committing.

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. production provenance — the request's production must be registered.
    2. no-actuation          — proposal :effect must be :propose.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    3. :publish without subject/talent consent AND rights clearance
       (music/BGM licensing, underlying IP) on the registered production.
    4. :assemble whose anime cut plan has zero completed layers.
    5. :assemble whose derived cut priority is \"retake\"
       (`anime.production/derive-cut-priority` found a stage marked retake).
    6. low confidence (< `confidence-floor`)."
  (:require [anime.production :as production]
            [animation-production.store :as store]))

(def confidence-floor 0.6)

(defn build-cut-plan
  "Pure function: walk `anime.production/layer-specs` against `cut`'s
  `:layers` map (`{layer-key {id-key uri-key ...}}`) to see which layers
  actually carry a produced id. Returns
  `{:cut-id ... :priority \"normal\"|\"retake\"|\"approved\"
    :completed-layers [...] :missing-layers [...]}`. `cut` may omit
  `:stage-status`, in which case priority derives from
  `anime.production/default-stage-status` (all-\"pending\", so \"normal\")."
  [cut]
  (let [layers (:layers cut)
        completed (reduce-kv
                   (fn [acc layer-key {:keys [id-key]}]
                     (if (get-in layers [layer-key id-key])
                       (conj acc layer-key)
                       acc))
                   []
                   production/layer-specs)]
    {:cut-id (:cut/id cut)
     :priority (production/derive-cut-priority
                (or (:stage-status cut) production/default-stage-status))
     :completed-layers completed
     :missing-layers (vec (remove (set completed) (keys production/layer-specs)))}))

(defn- hard-violations [{:keys [request proposal]} production-record]
  (cond-> []
    (nil? production-record)
    (conj {:rule :no-production
           :detail (str "未登録 production " (:production-id request))})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `animation-production.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool
    :escalations [...]}`."
  [request _context proposal store]
  (let [production-record (store/production store (:production-id request))
        hard (hard-violations {:request request :proposal proposal} production-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        clearance? (and (= :publish (:op proposal))
                        production-record
                        (not (and (:subjects-consented? production-record)
                                  (:rights-cleared? production-record))))
        cut-plan (when (= :assemble (:op proposal))
                   (build-cut-plan (:cut request)))
        empty-plan? (and cut-plan (empty? (:completed-layers cut-plan)))
        retake? (and cut-plan (= "retake" (:priority cut-plan)))
        escalations (cond-> []
                      clearance? (conj {:rule :rights-clearance
                                        :detail "公開には talent consent + rights clearance（音楽/BGM ライセンス、原作権）の人間承認が必要"})
                      empty-plan? (conj {:rule :empty-cut-plan
                                         :detail "anime cut plan に完成レイヤーが無い（cut を人間が確認）"})
                      retake? (conj {:rule :cut-in-retake
                                     :detail "cut のいずれかの工程が retake（derive-cut-priority、人間が確認）"})
                      low? (conj {:rule :low-confidence :detail conf}))]
    {:ok? (and (not hard?) (empty? escalations))
     :violations hard
     :escalations escalations
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (boolean (seq escalations)))}))
