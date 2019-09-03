(ns oc.lib.oauth
  (:require [clojure.edn :as edn])
  #?(:clj (:import [java.util Base64]
                   [java.net URLDecoder]
                   [java.nio.charset StandardCharsets])))

#?(:clj
   (defn encode-state-string
     [data]
     (let [encoder    (Base64/getUrlEncoder)
           data-bytes (-> data pr-str .getBytes)]
       (.encodeToString encoder data-bytes)))
   )

#?(:clj
   (defn decode-state-string
     [state-str]
     (let [url-decoded   (URLDecoder/decode state-str (.name StandardCharsets/UTF_8))
           b64-decoder   (Base64/getDecoder)
           decoded-bytes (.decode b64-decoder url-decoded)
           decoded-str   (String. decoded-bytes)]
       (edn/read-string decoded-str)))
   )


#?(:cljs
   (defn encode-state-string
     [data]
     (-> data
         pr-str
         js/btoa))
   )

#?(:cljs
   (defn decode-state-string
     [s]
     (-> s
         js/atob
         edn/read-string))
   )

