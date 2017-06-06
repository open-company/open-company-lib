(defproject open-company/lib "0.11.4"
  :description "OpenCompany Lib"
  :url "https://opencompany.com/"
  :license {
    :name "Mozilla Public License v2.0"
    :url "http://www.mozilla.org/MPL/2.0/"
  }

  :min-lein-version "2.7.1"

  ;; JVM memory
  :jvm-opts ^:replace ["-Xms128m" "-Xmx256m" "-server"]

  ;; All profile dependencies
  :dependencies [
    [org.clojure/clojure "1.9.0-alpha17" :scope "provided"] ; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/core.async "0.3.443"] ; Async programming and communication https://github.com/clojure/core.async
    [org.clojure/core.match "0.3.0-alpha4"] ; Erlang-esque pattern matching https://github.com/clojure/core.match
    [defun "0.3.0-RC1"] ; Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    [lockedon/if-let "0.1.0"] ; More than one binding for if/when macros https://github.com/LockedOn/if-let
    [com.stuartsierra/component "0.3.2"] ; Component Lifecycle https://github.com/stuartsierra/component
    [http-kit "2.3.0-alpha2"] ; HTTP client and server http://http-kit.org/
    [ring/ring-codec "1.0.1"] ; Utility function for encoding and decoding data https://github.com/ring-clojure/ring-codec
    ;; NB: Timbre needs to come before Sente due to conflicts in shared dependency: com.taoensso/encore
    [com.taoensso/timbre "4.10.0"] ; Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    ;; NB: Sente needs to come after Timbre due to conflicts in shared dependency: com.taoensso/encore
    [com.taoensso/sente "1.11.0"] ; WebSocket server https://github.com/ptaoussanis/sente
    [raven-clj "1.5.0"] ; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [liberator "0.14.1"] ; WebMachine (REST API server) port to Clojure https://github.com/clojure-liberator/liberator
    [amazonica "0.3.102"] ; A comprehensive Clojure client for the AWS API. https://github.com/mcohen01/amazonica
    [clj-jwt "0.1.1"] ; A Clojure library for JSON Web Token(JWT) https://github.com/liquidz/clj-jwt
    [com.apa512/rethinkdb "0.15.26"] ; RethinkDB client for Clojure https://github.com/apa512/clj-rethinkdb
    [cheshire "5.7.1"] ; JSON encoding / decoding https://github.com/dakrone/cheshire
    [clj-time "0.13.0"] ; Date and time lib https://github.com/clj-time/clj-time
    [com.climate/squeedo "0.1.4"] ; AWS SQS consumer https://github.com/TheClimateCorporation/squeedo
    [org.slf4j/slf4j-nop "1.8.0-alpha2"] ; Squeedo dependency
    [prismatic/schema "1.1.6"] ; Data validation https://github.com/Prismatic/schema
    [environ "1.1.0"] ; Environment settings from different sources https://github.com/weavejester/environ
    [hickory "0.7.1"] ; HTML as data https://github.com/davidsantiago/hickory
  ]

  :profiles {

    ;; QA environment and dependencies
    :qa {
      :dependencies [
        [philoskim/debux "0.2.1"] ; `dbg` macro around -> or let https://github.com/philoskim/debux
        [midje "1.9.0-alpha6"] ; Example-based testing https://github.com/marick/Midje
      ]
      :plugins [
        [lein-midje "3.2.1"] ; Example-based testing https://github.com/marick/lein-midje
        [jonase/eastwood "0.2.4"] ; Linter https://github.com/jonase/eastwood
        [lein-kibit "0.1.5"] ; Static code search for non-idiomatic code https://github.com/jonase/kibit
      ]
    }

    ;; Dev environment and dependencies
    :dev [:qa {
      :plugins [
        [lein-bikeshed "0.4.1"] ; Check for code smells https://github.com/dakrone/lein-bikeshed
        [lein-checkall "0.1.1"] ; Runs bikeshed, kibit and eastwood https://github.com/itang/lein-checkall
        [lein-pprint "1.1.2"] ; pretty-print the lein project map https://github.com/technomancy/leiningen/tree/master/lein-pprint
        [lein-ancient "0.6.10"] ; Check for outdated dependencies https://github.com/xsc/lein-ancient
        [lein-spell "0.1.0"] ; Catch spelling mistakes in docs and docstrings https://github.com/cldwalker/lein-spell
        [lein-deps-tree "0.1.2"] ; Print a tree of project dependencies https://github.com/the-kenny/lein-deps-tree
        [venantius/yagni "0.1.4"] ; Dead code finder https://github.com/venantius/yagni
        [com.jakemccrary/lein-test-refresh "0.20.0"] ; Autotest https://github.com/jakemcc/lein-test-refresh
      ]  
    }]

    :prod {}

    :repl-config [:dev {
      :dependencies [
        [org.clojure/tools.nrepl "0.2.13"] ; Network REPL https://github.com/clojure/tools.nrepl
        [aprint "0.1.3"] ; Pretty printing in the REPL (aprint ...) https://github.com/razum2um/aprint
      ]
      ;; REPL injections
      :injections [
        (require '[aprint.core :refer (aprint ap)]
                 '[clojure.stacktrace :refer (print-stack-trace)]
                 '[clojure.string :as s]
                 '[cheshire.core :as json])
      ]
    }]
  }


  :repl-options {
    :welcome (println (str "\n" (slurp (clojure.java.io/resource "oc/assets/ascii_art.txt")) "\n"
                      "OpenCompany Lib REPL\n"))
  }

  :aliases {
    "build" ["with-profile" "prod" "do" "clean," "deps," "uberjar"] ; clean and build code
    "repl" ["with-profile" "+repl-config" "repl"]
    "spell!" ["spell" "-n"] ; check spelling in docs and docstrings
    "autotest" ["with-profile" "qa" "do" "midje" ":autotest"] ; watch for code changes and run affected tests
    "test!" ["with-profile" "qa" "do" "clean," "deps," "compile," "midje"] ; build, and run all tests
    "bikeshed!" ["bikeshed" "-v" "-m" "120"] ; code check with max line length warning of 120 characters
    "ancient" ["ancient" ":all" ":allow-qualified"] ; check for out of date dependencies
  }

  ;; ----- Clojars release configuration -----

  :repositories [["release" {:url "https://clojars.org/repo"
                              :username :env/clojars_user
                              :password :env/clojars_pass}]]

  ;; ----- Code check configuration -----

  :eastwood {
    ;; Disable some linters that are enabled by default
    :exclude-linters [:constant-test :wrong-arity]
    ;; Enable some linters that are disabled by default
    :add-linters [:unused-namespaces :unused-private-vars]
    ;; Custom Eastwood config
    :config-files [".eastwood.clj"]

    ;; Exclude testing namespaces
    :tests-paths ["test"]
    :exclude-namespaces [:test-paths]
  }
)