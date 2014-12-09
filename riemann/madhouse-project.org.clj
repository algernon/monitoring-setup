;; Copyright (C) 2014  Gergely Nagy <algernon@madhouse-project.org>
;;
;; This work is free. You can redistribute it and/or modify it under the
;; terms of the Do What The Fuck You Want To Public License, Version 2,
;; as published by Sam Hocevar. See the COPYING file for more details.

(require '[clojure.string :as string])
(require '[org.spootnik.riemann.thresholds :refer [threshold-check]])

(def thresholds
  {"cpu-average/cpu-user" {:warning 30 :critical 60}
   "cpu-average/cpu-system" {:warning 30 :critical 60}
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

   "memory/memory-buffered Gb" {}
   "memory/memory-cached Gb" {}
   "memory/memory-free Gb" {:warning 0.10
                            :critical 0.05
                            :invert true}
   "memory/memory-used Gb" {}

   "swap/swap-cached Gb" {}
   "swap/swap-free Gb" {}
   "swap/swap-used Gb" {}
   "swap/swap_io-in" {}
   "swap/swap_io-out" {}

   "uptime/uptime" {:warning 1 :critical 0 :invert true}

   "df-root/df_complex-free Gb" {:warning 5 :critical 1 :invert true}

   "interface-wlan0/if_octets/rx Kb" {:warning 768 :critical 1024}
   "interface-wlan0/if_octets/tx Kb" {:warning 768 :critical 1024}
   "interface-eth0/if_octets/rx Kb" {}
   "interface-eth0/if_octets/tx Kb" {}

   "tail-auth/counter-sshd-invalid_user" {:warning 0 :critical 10}
   "tail-auth/counter-sshd-successful-logins" {}
   })

;;(logging/init :file "/srv/riemann/log/riemann.log")

(let [host "10.243.42.34"]
  (tcp-server :host host)
  (udp-server :host host)
  (ws-server  :host host)
  (repl-server :host host))

(periodically-expire 1)

(let [index (tap :index (index))]
  (streams
   (default :ttl 3
     (expired #(prn "Expired" %))
     (where (not (service #"^riemann "))
            (where (= (:plugin event) "df")
                   (adjust [:service str " Gb"]
                           (scale (/ 1 1024 1024 1024)
                                  (smap (threshold-check thresholds)
                                        index))))
            (where (= (:plugin event) "interface")
                   (adjust [:service str " Kb"]
                           (scale (/ 1 1024)
                                  (smap (threshold-check thresholds)
                                        index))))
            (where (= (:plugin event) "memory")
                   (adjust [:service str " Gb"]
                           (scale (/ 1 1024 1024 1024)
                                  (smap (threshold-check thresholds)
                                        index))))
            (where (and (= (:plugin event) "swap")
                        (= (:ds_type event) "gauge"))
                   (adjust [:service str " Gb"]
                           (scale (/ 1 1024 1024 1024)
                                  (smap (threshold-check thresholds)
                                        index))))
            (smap (threshold-check thresholds)
                  index)))))
