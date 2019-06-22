(ns reframe-forms.events
  (:require
   [clojure.string :as string]
   [re-frame.core :as rf]))


(defn keyword-or-int [x]
  (let [parsed (js/parseInt x)]
    (if (int? parsed)
      parsed
      (keyword x))))

(defn vec-of-keys [x]
  (if (vector? x)
    x
    (mapv keyword-or-int
          (if (qualified-keyword? x)
            (into (string/split (namespace x) ".")
                  (string/split (name x) "."))
            (string/split (name x) ".")))))

(rf/reg-sub
  :rff/query
  (fn [db [_ path]]
    (get-in db (vec-of-keys path))))

(rf/reg-event-db
  :rff/set
  (fn [db [_ path val]]
    (assoc-in db (vec-of-keys path) val)))

(rf/reg-event-db
  :rff/update
  (fn [db [_ path f]]
    (update-in db (vec-of-keys path) f)))
