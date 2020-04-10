(ns oc.unit.user
  (:require [midje.sweet :refer :all]
            [oc.lib.user :refer (name-for short-name-for)]))

(facts "The 'best' name is chosen for a user record"
  (name-for {}) => ""
  (name-for {:email "email"}) => "email"
  (name-for {:email "email" :last-name "last"}) => "last"
  (name-for {:email "email" :first-name "first"}) => "first"
  (name-for {:email "email" :last-name "last" :first-name "first"}) => "first last"
  (name-for {:email "email" :last-name "last" :first-name "first" :name "name"}) => "name")

(facts "The 'best' short name is chosen for a user record"
  (short-name-for {}) => ""
  (short-name-for {:email "email"}) => "email"
  (short-name-for {:email "email" :name "name"}) => "name"
  (short-name-for {:email "email" :name "name" :last-name "last"}) => "last"
  (short-name-for {:email "email" :name "name" :last-name "last" :first-name "first"}) => "first")