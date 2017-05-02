(ns oc.lib.proxy.sheets-chart
  "Proxy a request to a published chart from a Google Sheet and rewrite the response."
  (:require [taoensso.timbre :as timbre]
            [org.httpkit.client :as http]
            [ring.util.codec :as codec]
            [hickory.core :as h]
            [hickory.select :as s]
            [environ.core :refer (env)]))

(def chart-id "sheet-chart")

(defn- fix-script-string [s]
  (let [r0 #"(?i)(\"width\":\d+)"
        r01 #"(?i)(\"height\":\d+)"
        r1 #"(?i)safeDraw\(document.getElementById\('c'\)\)"
        ;; Regexp to match charts exported as HTML page
        r2 #"(?i)activeSheetId = '\d+'; switchToSheet\('\d+'\);"
        r3 #"(?i)\"containerId\":\"embed_\d+\""
        r4 #"(?i)posObj\('\d+', 'embed_\d+', 0, 0, 0, 0\);};"
        r5 #"(?i)\"legend\":\"\c+\""
        ;; Replace all regexp
        fixed-string (clojure.string/replace s r0 (str "\"width\": getViewportWidth()"))
        fixed-string-01 (clojure.string/replace fixed-string r01 (str "\"height\": getViewportHeight()"))
        fixed-string-1 (clojure.string/replace fixed-string-01 r1 (str "safeDraw(document.getElementById('" chart-id "'))"))
        fixed-string-2 (clojure.string/replace fixed-string-1 r2 (str "activeSheetId = '" chart-id "'; switchToSheet('" chart-id "');"))
        fixed-string-3 (clojure.string/replace fixed-string-2 r3 (str "\"containerId\":\"" chart-id "\""))
        fixed-string-4 (clojure.string/replace fixed-string-3 r4 (str "posObj('" chart-id "', '" chart-id "', 0, 0, 0, 0);};"))
        fixed-string-5 (clojure.string/replace fixed-string-4 r5 (str "\"legend\":{position:\"none\"}"))]
    fixed-string-5))

(defn- get-script-tag [s]
  (if (empty? (:src (:attrs s)))
    ;; Provided script, rewritten by us
    (str "<script type=\"text/javascript\">" (fix-script-string (apply str (:content s))) "</script>")
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
              (str "https://docs.google.com/" sheet-path "?" (codec/form-encode params)))]
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
          scripts (s/select (s/tag :script) parsed-html) ; extract the script tags
          script-strings (apply str (map #(get-script-tag %) scripts))
          output-html (str "<html><head>"
                            "<script type=\"text/javascript\" src=\"" (env :open-company-web-cdn) (if (env :open-company-proxy-deploy-key) (str "/" (env :open-company-proxy-deploy-key))) "/lib/GoogleSheets/GoogleSheets.js\"></script>"
                            "<link rel=\"stylesheet\" href=\"//maxcdn.bootstrapcdn.com/font-awesome/4.5.0/css/font-awesome.min.css\" />"
                            "<link rel=\"stylesheet\" href=\"" (env :open-company-web-cdn) (if (env :open-company-web-cdn) "/") (env :open-company-proxy-deploy-key) "/lib/GoogleSheets/GoogleSheets.css\" />"
                            "<link >"
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