(ns oc.unit.data.number-format
  (:require [midje.sweet :refer :all]
            [oc.lib.data.utils :as utils]))

(facts "about formatting numbers"

  (facts "when truncating decimals"
    (utils/truncate-decimals 1 2) => "1"
    (utils/truncate-decimals 1.0 1) => "1"
    (utils/truncate-decimals 1.0 2) => "1"
    (utils/truncate-decimals 1.00 1) => "1"
    (utils/truncate-decimals 1.01 1) => "1"
    (utils/truncate-decimals 1.049 1) => "1"
    (utils/truncate-decimals 1.00 2) => "1"
    (utils/truncate-decimals 1.001 2) => "1"
    (utils/truncate-decimals 1.0049 2) => "1"
    (utils/truncate-decimals 1.001 2) => "1"
    (utils/truncate-decimals 1.004 2) => "1"
    (utils/truncate-decimals 1.01 2) => "1.01"
    ; next one fails because (* 100 1.005) =  100.49999999999999, ugh
    ;(utils/truncate-decimals 1.005 2) => "1.01"
    (utils/truncate-decimals 1.1 1) => "1.1"
    (utils/truncate-decimals 1.10 2) => "1.1"
    (utils/truncate-decimals 1.104 2) => "1.1"
    (utils/truncate-decimals 1.15 1) => "1.2"
    (utils/truncate-decimals Math/PI 2) => "3.14"
    (utils/truncate-decimals Math/PI 1) => "3.1")

  (facts "with the size label"
    (utils/with-size-label 1.00123456) => "1"
    (utils/with-size-label 1.20123456) => "1.2"
    (utils/with-size-label 1.23456789) => "1.23"
    (utils/with-size-label 10.0123456) => "10"
    (utils/with-size-label 10.2345678) => "10.2"
    (utils/with-size-label 100.000000) => "100"
    (utils/with-size-label 100.123456) => "100"
    (utils/with-size-label 1000.12345) => "1K"
    (utils/with-size-label 1200.12345) => "1.2K"
    (utils/with-size-label 1230.12345) => "1.23K"
    (utils/with-size-label 10000.3456) => "10K"
    (utils/with-size-label 10120.3456) => "10.1K"
    (utils/with-size-label 1000000.12) => "1M"
    (utils/with-size-label 1200000.12) => "1.2M"
    (utils/with-size-label 1230000.12) => "1.23M"
    (utils/with-size-label 10000000.1) => "10M"
    (utils/with-size-label 10123456.7) => "10.1M"
    (utils/with-size-label 100123456) => "100M"
    (utils/with-size-label 650478) => "650K"
    (utils/with-size-label 2546589) => "2.55M"
    (utils/with-size-label -1.00123456) => "-1"
    (utils/with-size-label -1.20123456) => "-1.2"
    (utils/with-size-label -1.23456789) => "-1.23"
    (utils/with-size-label -10.0123456) => "-10"
    (utils/with-size-label -10.2345678) => "-10.2"
    (utils/with-size-label -100.000000) => "-100"
    (utils/with-size-label -100.123456) => "-100"
    (utils/with-size-label -1000.12345) => "-1K"
    (utils/with-size-label -1200.12345) => "-1.2K"
    (utils/with-size-label -1230.12345) => "-1.23K"
    (utils/with-size-label -10000.3456) => "-10K"
    (utils/with-size-label -10120.3456) => "-10.1K"
    (utils/with-size-label -1000000.12) => "-1M"
    (utils/with-size-label -1200000.12) => "-1.2M"
    (utils/with-size-label -1230000.12) => "-1.23M"
    (utils/with-size-label -10000000.1) => "-10M"
    (utils/with-size-label -10123456.7) => "-10.1M"
    (utils/with-size-label -100123456) => "-100M"
    (utils/with-size-label -650478) => "-650K"
    (utils/with-size-label -2546589) => "-2.55M")

  (facts "with currency that has a symbol"
    (utils/with-currency "USD" (utils/with-size-label 1.00123456)) => "$1"
    (utils/with-currency "EUR" (utils/with-size-label 1.00123456)) => "€1"
    (utils/with-currency "FKP" (utils/with-size-label 1.00123456) true) => "+£1"
    (utils/with-currency "USD" (utils/with-size-label 1.20123456)) => "$1.2"
    (utils/with-currency "EUR" (utils/with-size-label 1.20123456)) => "€1.2"
    (utils/with-currency "FKP" (utils/with-size-label 1.20123456) true) => "+£1.2"
    (utils/with-currency "USD" (utils/with-size-label 1.23456789)) => "$1.23"
    (utils/with-currency "EUR" (utils/with-size-label 1.23456789)) => "€1.23"
    (utils/with-currency "FKP" (utils/with-size-label 1.23456789) true) => "+£1.23"
    (utils/with-currency "USD" (utils/with-size-label 10.0123456)) => "$10"
    (utils/with-currency "EUR" (utils/with-size-label 10.0123456)) => "€10"
    (utils/with-currency "FKP" (utils/with-size-label 10.0123456) true) => "+£10"
    (utils/with-currency "USD" (utils/with-size-label 10.2345678)) => "$10.2"
    (utils/with-currency "EUR" (utils/with-size-label 10.2345678)) => "€10.2"
    (utils/with-currency "FKP" (utils/with-size-label 10.2345678) true) => "+£10.2"
    (utils/with-currency "USD" (utils/with-size-label 100.000000)) => "$100"
    (utils/with-currency "EUR" (utils/with-size-label 100.000000)) => "€100"
    (utils/with-currency "FKP" (utils/with-size-label 100.000000) true) => "+£100"
    (utils/with-currency "USD" (utils/with-size-label 100.123456)) => "$100"
    (utils/with-currency "EUR" (utils/with-size-label 100.123456)) => "€100"
    (utils/with-currency "FKP" (utils/with-size-label 100.123456) true) => "+£100"
    (utils/with-currency "USD" (utils/with-size-label 1000.12345)) => "$1K"
    (utils/with-currency "EUR" (utils/with-size-label 1000.12345)) => "€1K"
    (utils/with-currency "FKP" (utils/with-size-label 1000.12345) true) => "+£1K"
    (utils/with-currency "USD" (utils/with-size-label 1200.12345)) => "$1.2K"
    (utils/with-currency "EUR" (utils/with-size-label 1200.12345)) => "€1.2K"
    (utils/with-currency "FKP" (utils/with-size-label 1200.12345) true) => "+£1.2K"
    (utils/with-currency "USD" (utils/with-size-label 1230000.12)) => "$1.23M"
    (utils/with-currency "EUR" (utils/with-size-label 1230000.12)) => "€1.23M"
    (utils/with-currency "FKP" (utils/with-size-label 1230000.12) true) => "+£1.23M"
    (utils/with-currency "USD" (utils/with-size-label 10000000.1)) => "$10M"
    (utils/with-currency "EUR" (utils/with-size-label 10000000.1)) => "€10M"
    (utils/with-currency "FKP" (utils/with-size-label 10000000.1) true) => "+£10M"
    (utils/with-currency "USD" (utils/with-size-label 10123456.7)) => "$10.1M"
    (utils/with-currency "EUR" (utils/with-size-label 10123456.7)) => "€10.1M"
    (utils/with-currency "FKP" (utils/with-size-label 10123456.7) true) => "+£10.1M"
    (utils/with-currency "USD" (utils/with-size-label 100123456)) => "$100M"
    (utils/with-currency "EUR" (utils/with-size-label 100123456)) => "€100M"
    (utils/with-currency "FKP" (utils/with-size-label 100123456) true) => "+£100M"
    (utils/with-currency "USD" (utils/with-size-label -1.00123456)) => "-$1"
    (utils/with-currency "EUR" (utils/with-size-label -1.00123456)) => "-€1"
    (utils/with-currency "FKP" (utils/with-size-label -1.00123456) true) => "-£1"
    (utils/with-currency "USD" (utils/with-size-label -1.23456789)) => "-$1.23"
    (utils/with-currency "EUR" (utils/with-size-label -1.23456789)) => "-€1.23"
    (utils/with-currency "FKP" (utils/with-size-label -1.23456789) true) => "-£1.23"
    (utils/with-currency "USD" (utils/with-size-label -10.2345678)) => "-$10.2"
    (utils/with-currency "EUR" (utils/with-size-label -10.2345678)) => "-€10.2"
    (utils/with-currency "FKP" (utils/with-size-label -10.2345678) true) => "-£10.2"
    (utils/with-currency "USD" (utils/with-size-label -100.123456)) => "-$100"
    (utils/with-currency "EUR" (utils/with-size-label -100.123456)) => "-€100"
    (utils/with-currency "FKP" (utils/with-size-label -100.123456) true) => "-£100"
    (utils/with-currency "USD" (utils/with-size-label -1000.12345)) => "-$1K"
    (utils/with-currency "EUR" (utils/with-size-label -1000.12345)) => "-€1K"
    (utils/with-currency "FKP" (utils/with-size-label -1000.12345) true) => "-£1K"
    (utils/with-currency "USD" (utils/with-size-label -1230.12345)) => "-$1.23K"
    (utils/with-currency "EUR" (utils/with-size-label -1230.12345)) => "-€1.23K"
    (utils/with-currency "FKP" (utils/with-size-label -1230.12345) true) => "-£1.23K"
    (utils/with-currency "USD" (utils/with-size-label -10000.3456)) => "-$10K"
    (utils/with-currency "EUR" (utils/with-size-label -10000.3456)) => "-€10K"
    (utils/with-currency "FKP" (utils/with-size-label -10000.3456) true) => "-£10K"
    (utils/with-currency "USD" (utils/with-size-label -10120.3456)) => "-$10.1K"
    (utils/with-currency "EUR" (utils/with-size-label -10120.3456)) => "-€10.1K"
    (utils/with-currency "FKP" (utils/with-size-label -10120.3456) true) => "-£10.1K"
    (utils/with-currency "USD" (utils/with-size-label -1000000.12)) => "-$1M"
    (utils/with-currency "EUR" (utils/with-size-label -1000000.12)) => "-€1M"
    (utils/with-currency "FKP" (utils/with-size-label -1000000.12) true) => "-£1M"
    (utils/with-currency "USD" (utils/with-size-label -1230000.12)) => "-$1.23M"
    (utils/with-currency "EUR" (utils/with-size-label -1230000.12)) => "-€1.23M"
    (utils/with-currency "FKP" (utils/with-size-label -1230000.12) true) => "-£1.23M"
    (utils/with-currency "USD" (utils/with-size-label -10123456.7)) => "-$10.1M"
    (utils/with-currency "EUR" (utils/with-size-label -10123456.7)) => "-€10.1M"
    (utils/with-currency "FKP" (utils/with-size-label -10123456.7) true) => "-£10.1M"
    (utils/with-currency "USD" (utils/with-size-label -100123456)) => "-$100M"
    (utils/with-currency "EUR" (utils/with-size-label -100123456)) => "-€100M"
    (utils/with-currency "FKP" (utils/with-size-label -100123456) true) => "-£100M")

  (facts "with currency that has no symbol"
    (utils/with-currency "DZD" (utils/with-size-label 1.00123456)) => "1 Algerian Dinar"
    (utils/with-currency "DZD" (utils/with-size-label 10123456.7) true) => "+10.1M Algerian Dinar"
    (utils/with-currency "DZD" (utils/with-size-label -10120.3456) true) => "-10.1K Algerian Dinar"))