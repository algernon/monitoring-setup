;; Copyright (C) 2014  Gergely Nagy <algernon@madhouse-project.org>
;;
;; This work is free. You can redistribute it and/or modify it under the
;; terms of the Do What The Fuck You Want To Public License, Version 2,
;; as published by Sam Hocevar. See the COPYING file for more details.

(defmacro with-but-collectd
  [keys & children]

  (let [new-keys (merge {:host nil
                         :type nil
                         :type_instance nil
                         :ds_type nil
                         :ds_name nil
                         :ds_index nil
                         :plugin nil
                         :plugin_instance nil}
                        keys)]
    `(~'with ~new-keys ~@children)))

(defn float-to-percent
  [& children]
  (fn [e]
    (when (and e (:metric e))
      (let [new-event (assoc e :metric (* 100 (:metric e)))]
        (call-rescue new-event children)))))

(defn graphite-event-parser
  [{:keys [service] :as event}]

  (if-let [[source source-type node metric-type metric]
           (string/split service #"\." 5)]
    {:host node
     :service metric
     :metric (:metric event)
     :time (:time event)
     :tags [source metric-type]
     :ttl (* 60 5)}))
