;; boot show --updates
(set-env!
  :resource-paths #{"src"}
  :dependencies '[
    ;; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/clojure "1.9.0-alpha12" :scope "provided"]
    ;; Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    [com.taoensso/timbre "4.6.0"]
    ;; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [raven-clj "1.4.3"]
    ;; https://github.com/FasterXML/jackson-core
    [com.fasterxml.jackson.core/jackson-core "2.8.3"]
    ;; A comprehensive Clojure client for the AWS API. https://github.com/mcohen01/amazonica
    [amazonica "0.3.76"]
    ;; A Clojure library for JSON Web Token(JWT) https://github.com/liquidz/clj-jwt
    [clj-jwt "0.1.1"]
    ;; RethinkDB client for Clojure https://github.com/apa512/clj-rethinkdb
    [com.apa512/rethinkdb "0.15.26"]
    ;; JSON encoding / decoding https://github.com/dakrone/cheshire
    [cheshire "5.6.3"] 
    ;; Date and time lib https://github.com/clj-time/clj-time
    [clj-time "0.12.0"]

    ;; Boot tasks ==========================================
    ;; Example-based testing https://github.com/marick/Midje
    [midje "1.9.0-alpha5" :scope "midje"]
    ;; Midje test runner https://bitbucket.org/zilti/boot-midje
    [zilti/boot-midje "0.1.2"]])

(require '[clojure.java.io :as io]
         '[zilti.boot-midje :refer [midje]])

(set-env! :repositories [["clojars" {:url "https://clojars.org/open-company/lib"
                                     :username (System/getenv "CLOJARS_USER")
                                     :password (System/getenv "CLOJARS_PASS")}]])
(task-options!
 push {:ensure-clean true
       :repo "clojars"}
 pom {:project 'open-company/lib
      :url "https://github.com/open-company/open-company-lib"
      :version (str "0.0.1-" (subs (boot.git/last-commit) 0 7))
      :license {"MPL" "https://www.mozilla.org/media/MPL/2.0/index.txt"}})

(deftask test! []
  (set-env! :source-paths #(conj % "test"))
  (midje))

(deftask build []
  (comp (pom) (jar) (install)))