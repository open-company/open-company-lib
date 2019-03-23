(defproject open-company/lib "0.16.39-alpha4"
  :description "OpenCompany Common Library"
  :url "https://github.com/open-company/open-company-lib"
  :license {
    :name "GNU Affero General Public License Version 3"
    :url "https://www.gnu.org/licenses/agpl-3.0.en.html"
  }

  :min-lein-version "2.7.1"

  ;; JVM memory
  :jvm-opts ^:replace ["-Xms128m" "-Xmx256m" "-server"]

  ;; All profile dependencies
  :dependencies [
    ;; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/clojure "1.10.1-beta1" :scope "provided"]
    ;; Async programming and communication https://github.com/clojure/core.async
    [org.clojure/core.async "0.4.490"]
    ;; Erlang-esque pattern matching https://github.com/clojure/core.match
    [org.clojure/core.match "0.3.0-alpha5"]
    ;; Clojure reader https://github.com/clojure/tools.reader
    ;; NB: Not used directly, but a very common dependency, so pulled in for manual version management
    [org.clojure/tools.reader "1.3.2"]
    ;; Tools for writing macros https://github.com/clojure/tools.macro
    ;; NB: Not used directly, but a very common dependency, so pulled in for manual version management
    [org.clojure/tools.macro "0.1.5"]
    ;; Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    ;; NB: org.clojure/tools.macro is pulled in manually
    [defun "0.3.0-RC1" :exclusions [org.clojure/tools.macro]] 
    ;; More than one binding for if/when macros https://github.com/LockedOn/if-let
    [lockedon/if-let "0.3.0"]
    ;; Component Lifecycle https://github.com/stuartsierra/component
    [com.stuartsierra/component "0.4.0"]
    ;; ----------------------------------------------------------------------------------------
    ;; --- NB: DO NOT UPGRADE TO 2.4.0-alpha3
    [http-kit "2.3.0"] ; HTTP client and server http://http-kit.org/
    ;; --- it breaks WS connections returning an net::ERR_INVALID_HTTP_RESPONSE on connect ----
    ;; ----------------------------------------------------------------------------------------
    ;; Utility function for encoding and decoding data https://github.com/ring-clojure/ring-codec
    ;; NB: commons-codec gets picked up from amazonica
    [ring/ring-codec "1.1.1" :exclusions [commons-codec]]
    ;; Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    [com.taoensso/timbre "4.10.0" :exclusions [com.taoensso/encore]]
    ;; Java logging lib https://commons.apache.org/proper/commons-logging/
    ;; NB: Not used directly, but a very common dependency, so pulled in for manual version management
    [commons-logging "1.2"]
    ;; Java codec library https://commons.apache.org/proper/commons-codec/
    ;; NB: Not used directly, but a very common dependency, so pulled in for manual version management
    [commons-codec "1.12"]
    ;; WebSocket server https://github.com/ptaoussanis/sente
    ;; NB: timbre is pulled in manually
    ;; ----------------------------------------------------------------------------------------
    ;; --- NB: DO NOT UPDATE TO Sente 1.14.0-RC2
    [com.taoensso/sente "1.13.1" :exclusions [com.taoensso/timbre com.taoensso/encore]]
    ;; --- it has breaking changes to fix CSRF which we don't use since we rely on origin check,
    ;; --- our CSRF is static.
    ;; ----------------------------------------------------------------------------------------
    ;; Utility functions https://github.com/ptaoussanis/encore
    ;; NB: Not used directly, forcing this version of encore, a dependency of Timbre and Sente
    [com.taoensso/encore "2.107.0"]
    ;; Interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    ;; NB: commons-codec pulled in manually
    [raven-clj "1.6.0-alpha3" :exclusions [commons-codec]]
    ;; WebMachine (REST API server) port to Clojure https://github.com/clojure-liberator/liberator
    [liberator "0.15.3"] 
    ;; A comprehensive Clojure client for the AWS API. https://github.com/mcohen01/amazonica
    ;; NB: joda-time is pulled in by clj-time
    ;; NB: commons-logging is pulled in manually
    ;; NB: commons-codec is pulled in manually
    ;; NB: com.fasterxml.jackson.core/jackson-databind is pulled in manually
    ;; NB: com.amazonaw/aws-java-sdk-dynamodb is pulled in manually to get a newer version
    [amazonica "0.3.141"
     :exclusions [joda-time commons-logging commons-codec com.fasterxml.jackson.core/jackson-databind com.amazonaws/aws-java-sdk-dynamodb]]
    ;; DynamoDB SDK
    [com.amazonaws/aws-java-sdk-dynamodb "1.11.524"]
    ;; Data binding and tree for XML https://github.com/FasterXML/jackson-databind
    ;; NB: Not used directly, but a very common dependency, so pulled in for manual version management
    [com.fasterxml.jackson.core/jackson-databind "2.9.8"]
    ;; A Clojure library for JSON Web Token(JWT) https://github.com/liquidz/clj-jwt
    [clj-jwt "0.1.1"]
    ;; RethinkDB client for Clojure https://github.com/apa512/clj-rethinkdb
    [com.apa512/rethinkdb "0.15.26"]
    ;; JSON encoding / decoding https://github.com/dakrone/cheshire
    [cheshire "5.8.1"] 
    ;; Date and time lib https://github.com/clj-time/clj-time
    [clj-time "0.15.1"]
    ;; A clj-time inspired date library for clojurescript. https://github.com/andrewmcveigh/cljs-time
    [com.andrewmcveigh/cljs-time "0.5.2"]
    ;; AWS SQS consumer https://github.com/TheClimateCorporation/squeedo
    ;; NB: com.amazonaws/jmespath-java is pulled in by Amazonica
    ;; NB: com.amazonaws/aws-java-sdk-sqs is pulled in by Amazonica
    [com.climate/squeedo "1.1.1" :exclusions [com.amazonaws/jmespath-java com.amazonaws/aws-java-sdk-sqs]]
    ;; Squeedo dependency
    [org.slf4j/slf4j-nop "1.8.0-beta4"]
    ;; Data validation https://github.com/Prismatic/schema
    [prismatic/schema "1.1.10"]
    ;; Environment settings from different sources https://github.com/weavejester/environ
    [environ "1.1.0"]
    ;; HTML as data https://github.com/davidsantiago/hickory
    [hickory "0.7.1" :exclusions [org.clojure/clojurescript]]
    ;; Clojure wrapper for jsoup HTML parser https://github.com/mfornos/clojure-soup
    [clj-soup/clojure-soup "0.1.3"]
    ;; HTTP client https://github.com/dakrone/clj-http
    [clj-http "3.9.1"]
    ;; String manipulation library https://github.com/funcool/cuerdas
    [funcool/cuerdas "2.1.0"]
  ]

  :profiles {

    ;; QA environment and dependencies
    :qa {
      :dependencies [
        ;; Example-based testing https://github.com/marick/Midje
        ;; NB: org.clojure/tools.macro is pulled in manually
        ;; NB: clj-time is pulled in manually
        ;; NB: joda-time is pulled in by clj-time
        ;; NB: commons-codec pulled in manually
        [midje "1.9.6" :exclusions [joda-time org.clojure/tools.macro clj-time commons-codec]] 
      ]
      :plugins [
        ;; Example-based testing https://github.com/marick/lein-midje
        [lein-midje "3.2.1"]
        ;; Linter https://github.com/jonase/eastwood
        [jonase/eastwood "0.3.5"]
        ;; Static code search for non-idiomatic code https://github.com/jonase/kibit        
        [lein-kibit "0.1.6" :exclusions [org.clojure/clojure]]
      ]
    }

    ;; Dev environment and dependencies
    :dev [:qa {
      :plugins [
        ;; Check for code smells https://github.com/dakrone/lein-bikeshed
        ;; NB: org.clojure/tools.cli is pulled in by lein-kibit
        [lein-bikeshed "0.5.2" :exclusions [org.clojure/tools.cli]] 
        ;; Runs bikeshed, kibit and eastwood https://github.com/itang/lein-checkall
        [lein-checkall "0.1.1"]
        ;; pretty-print the lein project map https://github.com/technomancy/leiningen/tree/master/lein-pprint
        [lein-pprint "1.2.0"]
        ;; Check for outdated dependencies https://github.com/xsc/lein-ancient
        [lein-ancient "0.6.15"]
        ;; Catch spelling mistakes in docs and docstrings https://github.com/cldwalker/lein-spell
        [lein-spell "0.1.0"]
        ;; Dead code finder (use carefully, false positives) https://github.com/venantius/yagni
        [venantius/yagni "0.1.7" :exclusions [org.clojure/clojure]]
        ;; Autotest https://github.com/jakemcc/lein-test-refresh
        [com.jakemccrary/lein-test-refresh "0.24.0"]
      ]  
    }]

    :prod {}

    :repl-config [:dev {
      :dependencies [
        ;; Network REPL https://github.com/clojure/tools.nrepl
        [org.clojure/tools.nrepl "0.2.13"]
        ;; Pretty printing in the REPL (aprint ...) https://github.com/razum2um/aprint
        [aprint "0.1.3"]
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
                             :password :env/clojars_pass
                             :sign-releases false}]]

  ;; ----- Code check configuration -----

  :eastwood {
    ;; Disable some linters that are enabled by default
    ;; contant-test - just seems mostly ill-advised, logical constants are useful in something like a `->cond` 
    ;; wrong-arity - unfortunate, but it's failing on 3/arity of sqs/send-message
    ;; implicit-dependencies - uhh, just seems dumb
    :exclude-linters [:constant-test :wrong-arity :implicit-dependencies]
    ;; Enable some linters that are disabled by default
    :add-linters [:unused-namespaces :unused-private-vars]
    ;; Custom Eastwood config
    :config-files [".eastwood.clj"]

    ;; Exclude testing namespaces
    :tests-paths ["test"]
    :exclude-namespaces [:test-paths]
  }
)