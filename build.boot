;; boot show --updates
(set-env!
  :resource-paths #{"src"}
  :dependencies '[
    ;; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/clojure "1.9.0-alpha13" :scope "provided"]
    ;; Async programming and communication https://github.com/clojure/core.async
    [org.clojure/core.async "0.2.395"]
    ;; Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    [defun "0.3.0-alapha"]
    ;; Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    [com.taoensso/timbre "4.8.0-alpha1"]
    ;; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [raven-clj "1.4.3"]
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
    [midje "1.9.0-alpha5" :scope "test"]
    ;; Midje test runner https://bitbucket.org/zilti/boot-midje
    [zilti/boot-midje "0.2.2-SNAPSHOT" :scope "test"]])

(require '[zilti.boot-midje :refer [midje]])

(task-options!
 push {:ensure-clean false
       :repo-map {:url "https://clojars.org/repo/"
                  :username (System/getenv "CLOJARS_USER")
                  :password (System/getenv "CLOJARS_PASS")}}
 pom {:project 'open-company/lib
      :version (str "0.0.4.2-" (subs (boot.git/last-commit) 0 7))
      :url "https://opencompany.com/"
      :scm {:url "https://github.com/open-company/open-company-lib"}
      :license {"MPL" "https://www.mozilla.org/media/MPL/2.0/index.txt"}})

(deftask test! []
  (set-env! :source-paths #(conj % "test"))
  (midje))

(deftask build []
  (comp (pom) (jar) (install)))