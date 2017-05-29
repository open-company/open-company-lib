;; boot show --updates
(set-env!
  :resource-paths #{"src"}
  :dependencies '[
    ;; Lisp on the JVM http://clojure.org/documentation
    ;; NB: Unlike lein, this does not control the Clojure version, see BOOT_CLOJURE_VERSION in boot.properties
    [org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
    ;; Async programming and communication https://github.com/clojure/core.async
    [org.clojure/core.async "0.3.443"]
    ;; Erlang-esque pattern matching https://github.com/clojure/core.match
    [org.clojure/core.match "0.3.0-alpha4"]
    ;; Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    [defun "0.3.0-RC1"]
    ;; More than one binding for if/when macros https://github.com/LockedOn/if-let
    [lockedon/if-let "0.1.0"]
    ;; Component Lifecycle https://github.com/stuartsierra/component
    [com.stuartsierra/component "0.3.2"]
    ;; Web server http://http-kit.org/
    [http-kit "2.3.0-alpha2"]
    ;; NB: Timbre needs to come before Sente due to conflicts in shared dependency: com.taoensso/encore
    ;; Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    [com.taoensso/timbre "4.10.0"]
    ;; NB: Sente needs to come after Timbre due to conflicts in shared dependency: com.taoensso/encore
    ;; WebSocket server https://github.com/ptaoussanis/sente
    [com.taoensso/sente "1.11.0"]
    ;; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [raven-clj "1.5.0"]
    ;; WebMachine (REST API server) port to Clojure https://github.com/clojure-liberator/liberator
    [liberator "0.14.1"] 
    ;; A comprehensive Clojure client for the AWS API. https://github.com/mcohen01/amazonica
    [amazonica "0.3.101"]
    ;; A Clojure library for JSON Web Token(JWT) https://github.com/liquidz/clj-jwt
    [clj-jwt "0.1.1"]
    ;; RethinkDB client for Clojure https://github.com/apa512/clj-rethinkdb
    [com.apa512/rethinkdb "0.15.26"]
    ;; JSON encoding / decoding https://github.com/dakrone/cheshire
    [cheshire "5.7.1"] 
    ;; Date and time lib https://github.com/clj-time/clj-time
    [clj-time "0.13.0"]
    ;; AWS SQS consumer https://github.com/TheClimateCorporation/squeedo
    [com.climate/squeedo "0.1.4"]
    [org.slf4j/slf4j-nop "1.8.0-alpha2"] ; Squeedo dependency
    ;; Data validation https://github.com/Prismatic/schema
    [prismatic/schema "1.1.6"]
    ;; Environment settings from different sources https://github.com/weavejester/environ
    [environ "1.1.0"]
    ;; HTML as data https://github.com/davidsantiago/hickory
    [hickory "0.7.1"]
    ; Utility function for encoding and decoding data https://github.com/ring-clojure/ring-codec
    [ring/ring-codec "1.0.1"]

    ;; Boot tasks ==========================================
    ;; Example-based testing https://github.com/marick/Midje
    [midje "1.9.0-alpha6" :scope "test"]
    ;; Midje test runner https://bitbucket.org/zilti/boot-midje
    [zilti/boot-midje "0.2.2-SNAPSHOT" :scope "test"]])

(require '[zilti.boot-midje :refer [midje]])

(task-options!
 push {:ensure-clean true
       :repo-map {:url "https://clojars.org/repo/"
                  :username (System/getenv "CLOJARS_USER")
                  :password (System/getenv "CLOJARS_PASS")}}
 pom {:project 'open-company/lib
      :version (str "0.10.10-" (subs (boot.git/last-commit) 0 7))
      :url "https://opencompany.com/"
      :scm {:url "https://github.com/open-company/open-company-lib"}
      :license {"MPL" "https://www.mozilla.org/media/MPL/2.0/index.txt"}})

(deftask test! []
  (set-env! :source-paths #(conj % "test"))
  (midje))

(deftask build []
  (comp (pom) (jar) (install)))