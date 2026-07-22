(ns animation-production.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [animation-production.actor :as actor]
            [animation-production.store :as store]))

(defn- fresh-store []
  (-> (store/mem-store)
      (store/register-production! {:production/id "prod-1"
                                   :subjects-consented? false
                                   :rights-cleared? false})
      (store/register-production! {:production/id "prod-cleared"
                                   :subjects-consented? true
                                   :rights-cleared? true})))

(def cut
  {:cut/id "cut-ep01-003"
   :layers {:storyboard {:storyboard_id "sb-1"}
            :layout {:layout_id "ly-1"}
            :keyframe {:keyframe_id "kf-1"}
            :background {:background_id "bg-1"}}})

(deftest commits-a-clean-assemble-with-cut-plan
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "prod-1" :op :assemble
                 :cut cut :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (let [record (get-in result [:state :record])]
      (is (some? record))
      (testing "the committed record carries the anime cut plan"
        (is (= "cut-ep01-003" (get-in record [:cut-plan :cut-id])))
        (is (= "normal" (get-in record [:cut-plan :priority])))
        (is (= 4 (count (get-in record [:cut-plan :completed-layers]))))
        (is (contains? (set (get-in record [:cut-plan :completed-layers])) :storyboard))))
    (is (= 1 (count (store/records-of st "prod-1"))))))

(deftest holds-on-unregistered-production
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "no-such" :op :assemble
                 :cut cut :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-publish-until-clearance-approved
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:production-id "prod-1" :op :publish :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "prod-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= :publish (:op (get-in resumed [:state :record]))))
      (is (= 1 (count (store/records-of st "prod-1")))))))
