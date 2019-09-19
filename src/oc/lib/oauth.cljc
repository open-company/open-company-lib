(ns oc.lib.oauth
  "Encode and decode data with Base64"
  (:require [clojure.edn :as edn])
  #?(:clj (:import [java.util Base64]
                   [java.net URLDecoder]
                   [java.nio.charset StandardCharsets])))

(defn encode-state-string
  "Given a map of data return a string encoded with Base64."
  [data]
  #?(:clj
     (let [encoder    (Base64/getUrlEncoder)
           data-bytes (-> data pr-str .getBytes)]
       (.encodeToString encoder data-bytes)))
  #?(:cljs
     (-> data
         pr-str
         js/btoa)))

(defn decode-state-string
  "Given a Base64 encoded string return the decoded data."
  [state-str]
  #?(:clj
     (let [decode-url    #(URLDecoder/decode % (.name StandardCharsets/UTF_8))
           url-decoded   (decode-url state-str)
           b64-decoder   (Base64/getDecoder)
           decoded-bytes (.decode b64-decoder url-decoded)
           decoded-str   (String. decoded-bytes)
           state-map     (edn/read-string decoded-str)]
       (cond-> state-map
         (:redirect state-map) (update :redirect decode-url))))
  #?(:cljs
     (let [state-map (-> state-str
                         js/atob
                         edn/read-string)]
       (cond-> state-map
         (:redirect state-map) (update :redirect js/decodeURIComponent)))))
