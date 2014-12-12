;; Copyright (C) 2014  Gergely Nagy <algernon@madhouse-project.org>
;;
;; This work is free. You can redistribute it and/or modify it under the
;; terms of the Do What The Fuck You Want To Public License, Version 2,
;; as published by Sam Hocevar. See the COPYING file for more details.

(require '[clojure.string :as string])
(require '[org.spootnik.riemann.thresholds :refer [threshold-check]])

(include "inc/helpers.clj")
(include "inc/thresholds.clj")

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

            (where (not (nil? host))
                   (clock-skew
                    (with-but-collectd {:service "clock skew"
                                        :tags ["internal"]}
                      (rate 5 index))))
            index))))
