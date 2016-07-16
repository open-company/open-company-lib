;; boot show --updates
(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha10" :scope "provided"]
                 ;; Pure Clojure/Script logging library
                 ;; https://github.com/ptaoussanis/timbre
                 [com.taoensso/timbre "4.6.0"]
                 ;; Interface to Sentry error reporting
                 ;; https://github.com/sethtrain/raven-clj
                 [raven-clj "1.4.2"]
                 [com.fasterxml.jackson.core/jackson-core "2.5.3"]
                 ;; Durable atoms
                 ;; https://github.com/alandipert/enduro
                 [alandipert/enduro "1.2.0"]
                 ;; A comprehensive Clojure client for the entire Amazon AWS api.
                 ;; https://github.com/mcohen01/amazonica
                 [amazonica "0.3.66"]
                 ;; A Clojure library for JSON Web Token(JWT)
                 ;; https://github.com/liquidz/clj-jwt
                 [clj-jwt "0.1.1"]
                 ;; Boot tasks ==========================================
                 [adzerk/boot-test "1.1.2" :scope "test"] ; clojure.test runner
                 ])

(require '[clojure.java.io :as io]
         '[adzerk.boot-test :refer [test]])

(task-options!
 push {:ensure-clean true
       :repo "clojars"}
 pom {:project 'open-company/lib
      :version (str "0.0.0-" (subs (boot.git/last-commit) 0 7))
      :license {"MPL" "https://www.mozilla.org/media/MPL/2.0/index.txt"}})

(deftask test! []
  (set-env! :source-paths #(conj % "test"))
  (test))

(deftask build []
  (comp (pom) (jar) (install)))