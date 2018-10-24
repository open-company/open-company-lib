(ns oc.unit.text
  (:require [midje.sweet :refer :all]
            [oc.lib.text :refer (attribution strip-xss-tags)]))

(def item "foo")

(def authors [{:name "Sean"} {:name "Sean"} {:name "Nathan"} {:name "Nathan"} {:name "Sean"}
              {:name "Stuart"} {:name "Stuart"} {:name "Iacopo"} {:name "Iacopo"} {:name "Ryan"}])

(facts "about making attributions"
  (attribution 3 1 item (take 1 authors)) => "1 foo by Sean"
  (attribution 3 2 item (take 2 authors)) => "2 foos by Sean"
  (attribution 3 3 item (take 3 authors)) => "3 foos by Sean and Nathan"
  (attribution 3 4 item (take 4 authors)) => "4 foos by Sean and Nathan"
  (attribution 3 6 item (take 6 authors)) => "6 foos by Sean, Nathan and Stuart"
  (attribution 3 8 item (take 8 authors)) => "8 foos by Sean, Nathan, Stuart and others"
  (attribution 3 8 item (take 8 authors)) => "8 foos by Sean, Nathan, Stuart and others"
  (attribution 3 10 item authors) => "10 foos by Sean, Nathan, Stuart and others"
  (attribution 4 10 item authors) => "10 foos by Sean, Nathan, Stuart, Iacopo and others"
  (attribution 5 10 item authors) => "10 foos by Sean, Nathan, Stuart, Iacopo and Ryan"
  (attribution 1 10 item authors) => "10 foos by Sean and others")

(def pr-i-test-case "<ScRiPt and something here>hello</script> <StYlE>style</STYLE> <iNpUt and=\"soon\"><sPaN but-this=\"\">will be in</span>")
(def pr-test-case (clojure.string/lower-case pr-i-test-case))

(def br-tag-1 "<br/>")
(def br-tag-2 "<br/>")
(def anchor-tag "<a href=\"http://combat.org/\">world</a>")
(def span-tag "<span foo=\"bar\">in a span</span")

(def script-tag "<script>alert('gotcha!')</script>")
(def input-tag-1 "<input name='gotcha' type='email'/>")
(def input-tag-2 "<input name='gotcha' type='email'>output</input>")
(def style-tag "<style>I got it.</style>")

(def p-with-anchor (str "<p>Hello " anchor-tag "</p>"))
(def p-with-span (str "<p>Hello " span-tag "</p>"))
(def div-with-anchor (str "<div>Hello " anchor-tag "</div>"))
(def div-with-span (str "<div>Hello " span-tag "</div>"))

(def p-with-script (str "<p>Hello " script-tag "</p>"))
(def p-with-input (str "<p>Hello " input-tag-1 "</p>"))
(def p-with-style (str "<p>Hello " style-tag "</p>"))
(def div-with-script (str "<div>Hello " script-tag "</div>"))
(def div-with-input (str "<div>Hello " input-tag-1 "</div>"))
(def div-with-style (str "<div>Hello " style-tag "</div>"))


(facts "about stripping tags"
  
  (tabular
    (fact "no tags do nothing"
      (strip-xss-tags ?string) => ?result)
    ?string                   ?result
    ""                        ""
    "Hello world"             "Hello world"
    "Hello\nworld"            "Hello\nworld")
  
  (tabular
    (fact "basic formatting tags are left alone"
      (strip-xss-tags ?string) => ?result)
    ?string                   ?result
    br-tag-1                  br-tag-1
    br-tag-2                  br-tag-2
    anchor-tag                anchor-tag
    span-tag                  span-tag
    "<p/>"                    "<p/>"
    "<p></p>"                 "<p></p>"
    "<p>Hello world</p>"      "<p>Hello world</p>"
    p-with-anchor             p-with-anchor
    p-with-span               p-with-span
    "<div/>"                  "<div/>"
    "<div></div>"             "<div></div>"
    "<div>Hello world</div>"  "<div>Hello world</div>"
    div-with-anchor           div-with-anchor
    div-with-span             div-with-span)

  (tabular
    (fact "xss vulnerable tags are stripped"
      (strip-xss-tags ?string) => ?result)
    ?string                   ?result
    script-tag                "alert('gotcha!')"
    input-tag-1               ""
    input-tag-2               "output"
    style-tag                 "I got it."
    p-with-script             "<p>Hello alert('gotcha!')</p>"
    p-with-input              "<p>Hello </p>"
    p-with-style              "<p>Hello I got it.</p>"
    div-with-script           "<div>Hello alert('gotcha!')</div>"
    div-with-input            "<div>Hello </div>"
    div-with-style            "<div>Hello I got it.</div>")
  
  (fact "PR test case passes"
    (strip-xss-tags pr-test-case) => "hello style <span but-this=\"\">will be in</span>"
    (strip-xss-tags pr-i-test-case) => "hello style <sPaN but-this=\"\">will be in</span>"))