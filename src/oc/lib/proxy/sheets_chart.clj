(ns oc.lib.proxy.sheets-chart
  "Proxy a request to a published chart from a Google Sheet and rewrite the response."
  (:require [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [org.httpkit.client :as http]
            [ring.util.codec :as codec]
            [hickory.core :as h]
            [hickory.select :as hs]
            [environ.core :refer (env)]))

(def chart-id "sheet-chart")

(defn- fix-script-string [s keep-legend]
  (let [r0  #"(?i)(\"width\":\d+)"
        r01 #"(?i)(\\x22width\\x22:\d+)"
        r1  #"(?i)(\"height\":\d+)"
        r11  #"(?i)(\\x22height\\x22:\d+)"
        r2  #"(?i)safeDraw\(document\.getElementById\('c'\)\)"
        ;; Regexp to match charts exported as HTML page
        r3  #"(?i)activeSheetId = '\d+'; switchToSheet\('\d+'\);"
        r4  #"(?i)\"containerId\":\"embed_\d+\""
        r41 #"(?i)'elementId': 'embed_chart'"
        r5  #"(?i)posObj\('\d+', 'embed_\d+', 0, 0, 0, 0\);};"
        r6  #"(?i)\"legend\":\"((\bleft\b)|(\bright\b))\""
        r61 #"(?i)(\\x22legend\\x22:\\x22(left|right)\\x22)"
        r7  #"(?i)(function\s*onNumberFormatApiLoad\s*\(\s*\)\s*\{)"
        r71 #"(?i)(function\s*onChartsExportApiLoad\s*\(\s*\)\s*\{)"
        r8  #"(?i)'fallbackUri': '"
        has-legend-key (re-find (re-matcher #"(?i)([\"|\\x22]legend[\"|\\x22])" s))
        has-visible-legend (or (re-find (re-matcher r6 s)) (re-find (re-matcher r61 s)))
        ;; Replace all regexp
        ;; Replace the width value with a function that calculates the viewport width
        width-replace (str "(getViewportWidth()"
                        ;; Add 20% more width if the chart had a legend set on left or right
                        (when has-visible-legend
                          "+(getViewportWidth()/100*20)")
                        ")")
        width-replace1 "560"
        fixed-string-0  (clojure.string/replace s r0 (str "\"width\":" width-replace
                                                          ;; Add the legend key set to none if
                                                          ;; it's not a map chart (chartType: GeoChart)
                                                          ;; and it has no other legend key
                                                          (when (and (not keep-legend)
                                                                     (not has-legend-key))
                                                            ", \"legend\": \"none\"")))
        fixed-string-01 (clojure.string/replace fixed-string-0 r01 (str "\\\\x22width\\\\x22:" width-replace1
                                                                      ;; Add the legend key set to none if
                                                                      ;; it's not a map chart (chartType: GeoChart)
                                                                      ;; and it has no other legend key
                                                                      (when (and (not keep-legend)
                                                                                 (not has-legend-key))
                                                                        ", \\\\x22legend\\\\x22:\\\\x22none\\\\x22")))
        ;; Replace the height value with a function that calculates the viewport height
        fixed-string-1 (clojure.string/replace fixed-string-01 r1 (str "\"height\":(getViewportHeight())"))
        fixed-string-11 (clojure.string/replace fixed-string-1 r11 (str "\"height\":315"))
        ;; Replace the element id of the chart container
        fixed-string-2 (clojure.string/replace fixed-string-11 r2 (str "safeDraw(document.getElementById('" chart-id "'))"))
        ;; Replace the element id of the chart container for another type of shared chart
        fixed-string-3 (clojure.string/replace fixed-string-2 r3 (str "activeSheetId = '" chart-id "'; switchToSheet('" chart-id "');"))
        ;; Replace the container id with our chart-id
        fixed-string-4 (clojure.string/replace fixed-string-3 r4 (str "\"containerId\":\"" chart-id "\""))
        fixed-string-41 (clojure.string/replace fixed-string-4 r41 (str "'elementId': '" chart-id "'"))
        fixed-string-5 (clojure.string/replace fixed-string-41 r5 (str "posObj('" chart-id "', '" chart-id "', 0, 0, 0, 0);};"))
        ;; Set the legend to none if there is a legend key set to left or right already
        fixed-string-6 (clojure.string/replace fixed-string-5 r6 (str "\"legend\":\"none\""))
        fixed-string-61 (clojure.string/replace fixed-string-6 r61 (str "\\\\x22legend\\\\x22:\\\\x22none\\\\x22"))
        ;; Remove the body class that shows the chart placeholder icon while loading and rendering the chart
        fixed-string-7 (clojure.string/replace fixed-string-61 r7 (str "function onNumberFormatApiLoad(){ document.body.classList.remove(\"loading\");"))
        fixed-string-71 (clojure.string/replace fixed-string-7 r71 (str "document.addEventListener(\"DOMContentLoaded\", function(event) { document.body.classList.remove(\"loading\");}); function onChartsExportApiLoad(){ "))
        ;; Rewrite the fallbackUri param, prefix it with goodle docs url since it's relative
        fixed-string-8 (clojure.string/replace fixed-string-71 r8 (str "'fallbackUri': 'https:\\\\/\\\\/docs.google.com"))]
    fixed-string-8))

(defn- get-script-tag [s keep-legend]
  (if (empty? (:src (:attrs s)))
    ;; Provided script, rewritten by us
    (str "<script type=\"text/javascript\">" (fix-script-string (s/join (:content s)) keep-legend) "</script>")
    ;; Network loaded script, provided as a straight pass through
    (str "<script type=\"text/javascript\" src=\"/_/sheets-proxy-pass-through" (:src (:attrs s)) "\"></script>")))

(defn- proxy-sheets
  "
  Proxy requests to Google Sheets (needed for CORs). Rewrite the respones in a form ready for embedding as
  an iFrame. Return the response as a ring response (map).

  Used in development by the Web development service. Used in production by the OpenCompany Proxy Service.
  "
  [sheet-path params success-fn]
  (let [url (if (empty? params)
              (str "https://docs.google.com/" sheet-path)
              (str "https://docs.google.com/" sheet-path "?" (codec/form-encode (dissoc params "cache-buster"))))]
    (timbre/info "Proxying request to:" url)
    (let [{:keys [status body error]} @(http/request {:method :get
                                                      :url url
                                                      :headers {
                                                        "User-Agent" "curl/7.43.0"
                                                        "Accept" "*/*"}})]
      (timbre/info "Proxy request status:" status)
      (if error
        (do (timbre/error body) {:status status :body body})
        (success-fn status body)))))

(defn proxy-sheets-chart
  "
  Proxy requests to Google Sheets and rewrite the respones in a form ready for embedding as
  an iFrame. Return the response as a ring response (map).

  Used in development by the Web development service. Used in production by the OpenCompany Proxy Service.
  "
  [sheet-path params]
  (proxy-sheets sheet-path params (fn [status body]
    (let [parsed-html (h/as-hickory (h/parse body)) ; parse the HTML of the response
          scripts (hs/select (hs/tag :script) parsed-html) ; extract the script tags
          geo-chart-regex #"GeoChart"
          is-geo-chart (re-find (re-matcher geo-chart-regex body))
          script-strings (s/join (map #(get-script-tag % is-geo-chart) scripts))
          output-html (str "<html><head>"
                            "<script type=\"text/javascript\" src=\"" (env :open-company-web-cdn) (if (env :open-company-proxy-deploy-key) (str "/" (env :open-company-proxy-deploy-key))) "/lib/GoogleSheets/GoogleSheets.js\"></script>"
                            (when is-geo-chart
                              (str "<script async defer src=\"https://maps.googleapis.com/maps/api/js?key=" (env :open-company-web-gmap-key) "\" type=\"text/javascript\"></script>"))
                            "<link rel=\"stylesheet\" href=\"" (env :open-company-web-cdn) (if (env :open-company-web-cdn) "/") (env :open-company-proxy-deploy-key) "/lib/GoogleSheets/GoogleSheets.css\" />"
                            "</head>"
                            "<body class=\"loading\">"
                            script-strings
                            "<div id=\"" chart-id "\"></div>"
                            "</body></html>")]
      {:status 200 :body output-html}))))

(defn proxy-sheets-pass-through
  "
  Proxy requests through to Google Sheets (needed for CORs). Pass through the response as-is in a ring response (map).

  Used in development by the Web development service. Used in production by the OpenCompany Proxy Service.
  "
  [sheet-path params]
  (proxy-sheets sheet-path params (fn [status body] {:status 200 :body body :headers {"Content-Type" "text/html"}})))

(comment 

(require '[oc.lib.proxy.sheets-chart :as proxy] :reload)

;; https://docs.google.com/spreadsheets/d/1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc/pubchart?oid=1033950253&format=interactive
(proxy/proxy-sheets-chart "spreadsheets/d/1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc/pubchart" {:oid "1033950253"
                                                                                                  :format "interactive"})

;; https://docs.google.com/spreadsheets/d/1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc/pubchart?oid=1138076795&format=interactive
(proxy/proxy-sheets-chart "spreadsheets/d/1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc/pubchart" {:oid "=1138076795"
                                                                                                  :format "interactive"})

(proxy/proxy-sheets-pass-through "/static/spreadsheets2/caf7ecd791/ritz_charts_linked/ritz_charts_linked.nocache.js" {})

)