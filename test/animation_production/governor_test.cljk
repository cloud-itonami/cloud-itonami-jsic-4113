(ns animation-production.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [animation-production.governor :as governor]
            [animation-production.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-production! {:production/id "prod-1"
                                   :subjects-consented? true
                                   :rights-cleared? true})
      (store/register-production! {:production/id "prod-uncleared"
                                   :subjects-consented? false
                                   :rights-cleared? true})))

(def cut
  {:cut/id "cut-ep01-003"
   :layers {:storyboard {:storyboard_id "sb-1"}
            :keyframe {:keyframe_id "kf-1"}}})

(def retake-cut
  {:cut/id "cut-ep01-004"
   :layers {:storyboard {:storyboard_id "sb-2"}
            :keyframe {:keyframe_id "kf-2"}}
   :stage-status {"keyAnim" "retake"}})

(defn- proposal [op] {:op op :effect :propose :stake :low :confidence 0.95})

(deftest build-cut-plan-walks-anime-layer-specs
  (let [plan (governor/build-cut-plan cut)]
    (is (= "cut-ep01-003" (:cut-id plan)))
    (is (= "normal" (:priority plan)))
    (is (= 2 (count (:completed-layers plan))))
    (is (contains? (set (:completed-layers plan)) :storyboard))
    (is (contains? (set (:completed-layers plan)) :keyframe))
    (is (not (contains? (set (:completed-layers plan)) :background)))))

(deftest build-cut-plan-empty-layers
  (let [plan (governor/build-cut-plan {:cut/id "cut-empty" :layers {}})]
    (is (empty? (:completed-layers plan)))
    (is (= 8 (count (:missing-layers plan))))))

(deftest build-cut-plan-detects-retake-priority
  (is (= "retake" (:priority (governor/build-cut-plan retake-cut)))))

(deftest ok-on-clean-assemble
  (let [v (governor/check {:production-id "prod-1" :cut cut}
                          {} (proposal :assemble) (fresh-store))]
    (is (:ok? v))
    (is (not (:escalate? v)))))

(deftest hard-holds
  (testing "unregistered production"
    (let [v (governor/check {:production-id "no-such" :cut cut}
                            {} (proposal :assemble) (fresh-store))]
      (is (:hard? v))
      (is (some #(= :no-production (:rule %)) (:violations v)))))
  (testing "non-propose effect"
    (let [v (governor/check {:production-id "prod-1" :cut cut}
                            {} (assoc (proposal :assemble) :effect :write!) (fresh-store))]
      (is (:hard? v)))))

(deftest escalations
  (testing "publish without full talent consent/rights clearance"
    (let [v (governor/check {:production-id "prod-uncleared"}
                            {} (proposal :publish) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (some #(= :rights-clearance (:rule %)) (:escalations v)))))
  (testing "publish with clearance is ok"
    (let [v (governor/check {:production-id "prod-1"}
                            {} (proposal :publish) (fresh-store))]
      (is (:ok? v))))
  (testing "assemble with an empty anime cut plan"
    (let [v (governor/check {:production-id "prod-1" :cut {:cut/id "cut-empty" :layers {}}}
                            {} (proposal :assemble) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (some #(= :empty-cut-plan (:rule %)) (:escalations v)))))
  (testing "assemble whose cut is flagged retake"
    (let [v (governor/check {:production-id "prod-1" :cut retake-cut}
                            {} (proposal :assemble) (fresh-store))]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (some #(= :cut-in-retake (:rule %)) (:escalations v)))))
  (testing "low confidence"
    (let [v (governor/check {:production-id "prod-1" :cut cut}
                            {} (assoc (proposal :assemble) :confidence 0.2) (fresh-store))]
      (is (:escalate? v)))))
