(ns oc.lib.data.utils
  "Utility functions for the 'data' (growth and finance) topics."
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [oc.lib.data.iso4217 :as iso4217]))

(def intervals #{:weekly :monthly :quarterly})

(def quarterly-period (f/formatter "YYYY-MM"))
(def monthly-period (f/formatter "YYYY-M"))
(def weekly-period (f/formatter :date))

(def yearly-date (f/formatter "YYYY"))
(def quarterly-date (f/formatter "MMM YYYY"))
(def monthly-date (f/formatter "MMM YYYY"))
(def weekly-date (f/formatter "d MMM YYYY"))

(defn in?
  "true if seq contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn not-zero? [value]
  (and (number? value) (not= 0 value) (not= 0.0 value)))

(defn fix-runway [runway]
  (if (neg? runway)
    (Math/abs runway)
    0))

(defn calc-runway [cash burn-rate]
  (int (* (/ cash burn-rate) 30)))

(defn remove-trailing-zero
  "Remove the last zero(s) in a numeric string only after the dot.
   Remote the dot too if it is the last char after removing the zeros"
   [string]
   (cond

    (and (not= (.indexOf string ".") -1) (= (last string) "0"))
    (remove-trailing-zero (subs string 0 (dec (count string))))

    (= (last string) ".")
    (subs string 0 (dec (count string)))

    :else
    string))

(defn pluralize [n]
  (if (> n 1)
    "s"
    ""))

(defn get-rounded-runway [runway-days & [flags]]
  (let [abs-runway-days (Math/abs runway-days)]
    (cond
      ; days
      (< abs-runway-days 7)
      (let [days (int abs-runway-days)]
        (str days " day" (pluralize days)))
      ; weeks
      (< abs-runway-days (* 30 3))
      (let [weeks (int (/ abs-runway-days 7))]
        (str weeks " week" (pluralize weeks)))
      ; months
      (< abs-runway-days (* 30 12))
      (if (in? flags :round)
        (let [months (int (/ abs-runway-days 30))
              fixed-months (if (in? flags :remove-trailing-zero)
                             (remove-trailing-zero (str months))
                             (str months))]
          (str fixed-months " month" (pluralize months)))
        (let [months (quot abs-runway-days 30)]
          (str months " month" (pluralize months))))
      ; years
      :else
      (if (in? flags :round)
        (let [years (int (/ abs-runway-days (* 30 12)))
              fixed-years (if (in? flags :remove-trailing-zero)
                            (remove-trailing-zero (str years))
                            (str years))]
          (str fixed-years " year" (pluralize years)))
        (let [years (quot abs-runway-days (* 30 12))]
          (str years " year" (pluralize years)))))))

(defn remove-trailing
  ""
  [value]
  {:pre [(string? value)]
   :post [(string? %)]}
  (if-not (or (and (s/ends-with? value "0") (.contains value "."))
              (s/ends-with? value "."))
    value
    (remove-trailing (subs value 0 (dec (count value))))))

(defn truncate-decimals
  "Round and truncate to a float value to at most the specified number of decimal places,
  leaving no trailing 0's to the right of the decimal."
  [value decimals]
  {:pre [(number? value) (pos? decimals) (integer? decimals)]
   :post [(string? %)]}
  (let [exp (Math/pow 10 decimals)]
    (remove-trailing (format (str "%." decimals "f") (float (/ (Math/round (* exp value)) exp))))))

(defn with-size-label [orig-value]
  (when orig-value
    (let [neg (neg? orig-value)
          value (Math/abs orig-value)
          short-value (cond
                        ; 100M
                        (>= value 100000000)
                        (str (truncate-decimals(int (/ value 1000000)) 2) "M")
                        ; 10.0M
                        (>= value 10000000)
                        (str (truncate-decimals (/ value 1000000) 1) "M")
                        ; 1.00M
                        (>= value 1000000)
                        (str (truncate-decimals (/ value 1000000) 2) "M")
                        ; 100K
                        (>= value 100000)
                        (str (truncate-decimals (int (/ value 1000)) 2) "K")
                        ; 10.0K
                        (>= value 10000)
                        (str (truncate-decimals (/ value 1000) 1) "K")
                        ; 1.00K
                        (>= value 1000)
                        (str (truncate-decimals (/ value 1000) 2) "K")
                        ; 100
                        (>= value 100)
                        (str (truncate-decimals (int value) 2))
                        ; 10.0
                        (>= value 10)
                        (str (truncate-decimals value 1))
                        ; 1.00
                        :else
                        (str (truncate-decimals value 2)))]
      (if neg
        (str "-" short-value)
        short-value))))

(defn with-currency
  "Combine the value with the currency indicator, if available."
  [currency value]
  (let [value-string (str value)
        negative? (s/starts-with? value "-")
        neg (when negative? "-")
        clean-value (if negative? (subs value-string 1) value-string)
        currency-key (keyword currency)
        currency-entry (iso4217/iso4217 currency-key)
        currency-symbol (if currency-entry (:symbol currency-entry) false)
        currency-text (if currency-entry (:text currency-entry) false)]
    (if currency-symbol 
      (str neg currency-symbol clean-value)
      (if currency-text
        (str value-string " " currency-text)
        value-string))))

(defn- value-output [value]
  (cond
    (integer? value) (format "%,d" (biginteger value))
    (float? value) (str (format "%,d" (biginteger (Math/floor value))) "." (last (s/split (str value) #"\.")))
    :else "0"))

(defn with-format [format-symbol value]
  (cond
    (= format-symbol "%") (str (value-output value) "%")
    (not (nil? format-symbol)) (with-currency format-symbol (value-output value))
    :else (value-output value)))

(defn- get-quarter-from-month [month & [flags]]
  (let [short-str (in? flags :short)]
    (cond
      (and (>= month 1) (<= month 3))
      (if short-str
        "Q1"
        "January - March")
      (and (>= month 4) (<= month 6))
      (if short-str
        "Q2"
        "April - June")
      (and (>= month 7) (<= month 9))
      (if short-str
        "Q3"
        "July - September")
      (and (>= month 10) (<= month 12))
      (if short-str
        "Q4"
        "October - December"))))

(defn format-period [interval period]
  (s/upper-case 
    (case interval
      "quarterly" (str (get-quarter-from-month (t/month period) [:short]) " " (f/unparse yearly-date period))
      "weekly" (f/unparse weekly-date period)
      (f/unparse monthly-date period))))

(defn parse-period [interval value]
  (case interval
    "quarterly" (f/parse quarterly-period value)
    "weekly" (f/parse weekly-period value)
    (f/parse monthly-period value)))

(defun contiguous
  "Starting with the most recent period in the sequence, return the longest list of contiguous periods that exists.

  E.g.

  (contiguous ['2016-10' 2016-08' '2016-09' '2016-06'] :monthly) => ['2016-10' '2016-09' '2016-08']
  "

  ;; Defaults to monthly
  ([periods :guard sequential?] (contiguous periods :monthly))

  ;; Use keyword for interval
  ([periods :guard sequential? interval :guard string?] (contiguous periods (keyword interval)))

  ;; Empty case
  ([_periods :guard #(and (sequential? %) (empty? %)) _interval :guard intervals] [])

  ;; Only one period case
  ([periods :guard #(and (sequential? %) (= (count %) 1)) _interval :guard intervals] (vec periods))

  ;; Gone through all the periods with continuity, return them all as contiguous
  ([_periods :guard empty? contiguous-periods _interval] contiguous-periods)

  ;; All intervals - initial state
  ([periods :guard sequential? interval :guard intervals]
  (let [sorted-periods (reverse (sort periods))]
    (contiguous (rest sorted-periods) [(first sorted-periods)] interval))) ; start w/ the oldest period as contiguous list
  
  ;; Weekly - initial state
  ([periods :guard sequential? contiguous-periods :guard vector? interval :guard #{:weekly}]
    (contiguous periods contiguous-periods interval weekly-period (t/weeks 1)))
  ;; Monthly - initial state
  ([periods :guard sequential? contiguous-periods :guard vector? interval :guard #{:monthly}]
    (contiguous periods contiguous-periods interval monthly-period (t/months 1)))
  ;; Quarterly- initial state
  ([periods :guard sequential? contiguous-periods :guard vector? interval :guard #{:quarterly}]
    (contiguous periods contiguous-periods interval quarterly-period (t/months 3)))
  
  ;; All intervals - progressing
  ([periods :guard sequential? contiguous-periods :guard vector? interval :guard intervals formatter one-interval]
    (let [current-period (f/parse formatter (last contiguous-periods))
          prior-period (f/parse formatter (first periods))
          prior-contiguous-period (t/minus current-period one-interval)]
      (if (= prior-period prior-contiguous-period)
        (contiguous (rest periods) (conj contiguous-periods (first periods)) interval) ; we found another!
        contiguous-periods)))) ; That's all there is that are contiguous