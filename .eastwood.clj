;; NOTE: This file is *evaluated* by Eastwood before it starts

;; This file contains some configuration for disabling warnings that
;; Eastwood uses by default.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in defun, version 0.3.0-RC1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'defun.core/defun 'defun.core/defun-}
  :within-depth nil
  :reason "Many clojure.core.match/match macro expansions contain expressions of the form (and expr).  This is normal, and probably simplifies the definition of match."})