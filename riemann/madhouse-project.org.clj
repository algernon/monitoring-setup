;; Copyright (C) 2014  Gergely Nagy <algernon@madhouse-project.org>
;;
;; This work is free. You can redistribute it and/or modify it under the
;; terms of the Do What The Fuck You Want To Public License, Version 2,
;; as published by Sam Hocevar. See the COPYING file for more details.

(require '[clojure.string :as string])
(require '[org.spootnik.riemann.thresholds :refer [threshold-check]])

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
    (when e
      (let [new-event (assoc e :metric (* 100 (:metric e)))]
        (call-rescue new-event children)))))

(def thresholds
  {"cpu-average/cpu-user" {:warning 30 :critical 60}
   "cpu-average/cpu-system" {:warning 30 :critical 60}
   "cpu-average/cpu-used" {:warning 60 :critical 90}
   "cpu-average/cpu-nice" {:warning 50 :critical 20}
   "cpu-average/cpu-idle" {:warning 50 :critical 20 :invert true}
   "cpu-average/cpu-steal" {:warning 50 :critical 20}

   "load/load/shortterm" {:warning 3 :critical 4}
   "load/load/midterm" {:warning 3 :critical 4}
   "load/load/longterm" {:warning 3 :critical 4}

   "processes/ps_state-blocked" {:warning 4 :critical 8}
   "processes/ps_state-paging" {:warning 4 :critical 8}
   "processes/ps_state-running" {:warning 16 :critical 24}
   "processes/ps_state-sleeping" {:warning 500 :critical 1000}
   "processes/ps_state-stopped" {:warning 1 :critical 8}
   "processes/ps_state-zombies" {:warning 0 :critical 8}

   "memory/memory-buffered" {}
   "memory/memory-cached" {}
   "memory/memory-free" {:warning 0.10
                         :critical 0.05
                         :invert true}
   "memory/memory-used" {}
   "memory/percent-used" {:warning 80
                          :critical 98}

   "swap/swap-cached" {}
   "swap/swap-free" {}
   "swap/swap-used" {}
   "swap/swap_io-in" {}
   "swap/swap_io-out" {}

   "uptime/uptime" {:warning 1 :critical 0 :invert true}

   "df-root/percent_bytes-free" {:warning 10 :critical 6 :invert true}

   "interface-wlan0/if_octets/rx" {:warning 768 :critical 1024}
   "interface-wlan0/if_octets/tx" {:warning 768 :critical 1024}
   "interface-eth0/if_octets/rx" {}
   "interface-eth0/if_octets/tx" {}

   "tail-auth/counter-sshd-invalid_user" {:warning 0 :critical 10}
   "tail-auth/counter-sshd-successful-logins" {}
   })

(logging/init :file "/srv/riemann/log/riemann.log"
              :console false)

(let [host "10.243.42.34"]
  (tcp-server :host host)
  (udp-server :host host)
  (ws-server  :host host)
  (repl-server :host host))

(def default-ttl 30)
(periodically-expire 1)

(let [index (smap (threshold-check thresholds)
                  (tap :index (index)))]
  (streams
   (default :ttl default-ttl
     (expired #(info "Expired" %))
     (where (not (service #"^riemann "))

            (where (or (service #"^load/load/")
                       (service #"^memory/"))
                   index
                   (by :service
                       (coalesce
                        (smap folds/sum
                              (with-but-collectd {:tags ["summary"]
                                                  :ttl default-ttl}
                                index)))))

            (where (service #"^interface-.*/if_octets/[tr]x$")
                   index
                   (coalesce
                    (smap folds/sum
                          (with-but-collectd {:service "total network traffic"
                                              :tags ["summary"]
                                              :ttl default-ttl
                                              :state "ok"}
                            index))))

            (where (not (tagged "summary"))
                   (with :service "distinct hosts"
                         (coalesce
                          (smap folds/count
                                (with-but-collectd {:tags ["summary"]
                                                    :ttl default-ttl
                                                    :state nil}

                                  reinject)))))

            (by [:host]
                (project [(service "cpu-average/cpu-system")
                          (service "cpu-average/cpu-user")]
                         (smap folds/sum
                               (with {:service "cpu-average/cpu-used"
                                      :ttl default-ttl}
                                     index)))

                (project [(service "memory/memory-used")
                          (service "memory/memory-free")
                          (service "memory/memory-cached")
                          (service "memory/memory-buffered")]
                         (smap folds/sum
                               (with {:service "memory/memory-total"
                                      :ttl default-ttl
                                      :tags ["summary"]}
                                     reinject)))

                (project [(service "memory/memory-used")
                          (service "memory/memory-total")]
                         (smap folds/quotient
                               (with {:service "memory/percent-used"
                                      :ttl default-ttl}
                                     (float-to-percent index)))))

            (clock-skew
             (rate 5
                   (smap folds/non-nil-metrics
                         (with-but-collectd {:service "clock skew"
                                             :ttl default-ttl}
                           index))))
            index))))
