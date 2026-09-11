# cloud-itonami-jsic-4113

Open Industry Blueprint for **JSIC 4113**: アニメーション制作業 (Animation
Production), Japan Standard Industrial Classification.

This repository designs a forkable OSS business for an independent animation
production studio: a studio that keeps its own cuts, layers, rights records
and delivery history instead of renting a closed production-management SaaS.

## Classification-gap rationale (why JSIC, not ISCO/ISIC)

ADR-2607023000 (creative -ka layer decomposition) originally assigned
**animeka** (アニメ制作) to **ISCO-08 `2166`** (Graphic and Multimedia
Designers) as the "existing repo" for the 職能 (occupation) layer. In
practice that assignment does not hold:

- **`cloud-itonami-isco-2166`** is already a fully implemented, unrelated
  business — an *Independent Graphic Design Studio* built around a
  print-proofing robot premise. Its README and `blueprint.edn` contain zero
  reference to animeka, `kotoba-lang/anime`, or animation production of any
  kind. ISCO-08 genuinely has no unit group more specific than 2166 for
  "animator" — 2166 is a real, coarse international standard that folds
  animation into general graphic/multimedia design.
- The natural ISIC fallback, **`cloud-itonami-isic-5911`** (Motion Picture,
  Video and TV Programme Production, ISIC Rev.4), is likewise already
  claimed by a generic, animeka-unrelated production-operations-coordination
  actor.

Both the ISCO-08 and ISIC axes were checked and found genuinely unable to
express this vertical without colliding with an already-implemented,
unrelated business. Following the exact precedent of
[`cloud-itonami-jsic-4721`](https://github.com/cloud-itonami/cloud-itonami-jsic-4721)
(the first `jsic-` repo, ADR-2607177500: *"ISICで表現できない場合にのみ補完的
にJSICを使う、スコープの狭い決定"* — JSIC is a narrow supplementary axis, used
only when ISIC genuinely cannot express the vertical), this actor is
classified on the **Japan Standard Industrial Classification (JSIC)** axis
instead:

- 大分類 G 情報通信業 (Information and Communications)
- 中分類 41 映像・音声・文字情報制作業 (Video/Audio/Text Information Production)
- 小分類 411 映像情報制作・配給業 (Video Information Production and Distribution)
- 細分類 **4113 アニメーション制作業** (Animation Production)

This holds in both the 2013-10 and current 2023-07 JSIC revisions. Sources:
https://www.e-stat.go.jp/classifications/terms/10/03/4113 and
https://www.e-stat.go.jp/classifications/terms/10/04/4113 .

### Numeric-collision check ("does '4113' mean something else elsewhere?")

`cloud-itonami-jsic-4721`'s own README documents a real trap: ISIC's own
numeric code "4721" independently means something unrelated (food/beverage/
tobacco retail) to JSIC's 4721 (refrigerated warehousing). The same check was
run for "4113" before adopting it here:

- **ISIC** (checked against the Rev.4 structure — division 41 "Construction
  of buildings" contains only class **4100**, no 4113; no other division
  defines class 4113 either): **no ISIC class 4113 exists.** No collision.
- **ISCO-08**: no unit group 4113 exists under sub-major group 41 (General
  and keyboard clerks — minor group 411 "General office clerks" has only
  unit 4110) or anywhere else in the current ISCO-08 structure. The digits
  "4113" *do* appear once in ILO's own ISCO-88↔ISCO-08 correspondence
  tables, but only as a **legacy ISCO-88** code for "Data Entry Operators"
  (reclassified under ISCO-08 as unit group 4132) — an unrelated occupation
  in a superseded classification version, not a live ISCO-08 code, and not a
  collision with anything `cloud-itonami-isco-*` uses (those repos key off
  ISCO-08, never ISCO-88).

**Conclusion: no live numeric collision found** for JSIC 4113 against either
ISIC or ISCO-08. Future readers do not need to re-check this.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a motion-capture-stage /
camera-dolly robot performs physical reference-capture moves (actor
mocap staging, multi-angle background reference photography) under an actor
that proposes actions and an independent **Animation Production Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions (such as publishing footage without
talent consent / rights clearance, or overriding a stage-safety constraint)
require human sign-off. `robotics: true` follows the same precedent already
set by every other creative-domain occupation blueprint in this fleet
(`cloud-itonami-isco-2651` Visual Artists, `-2652` Musicians, `-2654` Video
Production — all `:itonami.blueprint/robotics true` with a physical-capture
robot premise of their own).

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html) —
pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
client brief + storyboard/cut sheet + voice/BGM assets
        |
        v
Production Advisor -> Animation Production Governor -> assemble/publish,
        |                                              or human sign-off
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
publish footage without rights clearance, or suppress an operating record.

## Capability layer

Craft library (public, kotoba-lang):
[`anime`](https://github.com/kotoba-lang/anime) — work-agnostic cut
hierarchy (work → episode → scene → cut), 12 production stages, layer
vocabulary (`layer-specs`) and pure identity helpers. The private reference
implementation is gftdcojp's `ai-gftd-animeka` actor (ADR-2607023000: コード
は kotoba-lang、職能は cloud-itonami-jsic、商売は gftdcojp).

Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

## Reference actor (`:maturity :implemented`)

Full itonami Actor pattern (like
[`cloud-itonami-isco-2654`](https://github.com/cloud-itonami/cloud-itonami-isco-2654)):
a real [`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph` with Advisor and Governor as distinct nodes and
human-in-the-loop interrupt/resume. The governor's cut sanity check walks
the actual `anime.production/layer-specs` and
`anime.production/derive-cut-priority` (kotoba-lang craft lib,
ADR-2607023000) to build a **cut plan**, and assemble commits carry the
built plan so what was approved is exactly what was assembled.

- HARD → `:hold`: unregistered production, non-`:propose` effect.
- ESCALATE → `:request-approval` (human-signed): publish without talent
  consent + rights clearance, assemble whose cut plan has zero completed
  layers, assemble whose cut is flagged `"retake"`, low confidence.

```bash
kbb -M:test
```

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
