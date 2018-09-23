(ns reframe-forms.core
  (:require
   [cljs.reader as reader]
   [clojure.string :as string]
   [reagent.core :as r]
   [re-frame.core :as rf]))

; Setting default value/checked: the idiomatic way of doing this is 
; setting the default value of the :name path to be the one you want
; to be the default.

(def vec-of-keys
  (memoize
    (fn [x]
      (if (vector? x)
        x
        (mapv keyword-or-int
              (if (qualified-keyword? x)
                (into (string/split (namespace k) ".")
                      (string/split (name k) "."))
                (string/split (name k) ".")))))))


; -----------------------------------------------------------------------------
; Input Components Utils
; -----------------------------------------------------------------------------

(defn get-stored-val [path]
  (rf/subscribe [:query path]))

(defn target-value [event]
  (.-value (.-target event)))

(defn format-number [event]
  (when-not (empty? (target-value event))
    (let [parsed (js/parseFloat n)]
      (when-not (js/isNaN parsed)
        parsed))))

(defn read-string*
  "Same as cljs.reader/read-string, except that returns a string when
  read-string returns a symbol."
  [x]
  (let [parsed (reader/read-string x)]
    (if (symbol? parsed)
      (str parsed)
      parsed)))

(defn attrs-on-change-set [attrs f]
  (assoc attrs :on-change #(rf/dispatch [:set (:name attrs) (f %)])))

(defn attrs-on-change-update [attrs f]
  (assoc attrs :on-change #(rf/dispatch [:update (:name attrs) f])))

(defn attrs-on-change-multiple [attrs val]
  (assoc attrs :on-change 
    #(rf/dispatch [:update (:name attrs)
                   (fn [acc]
                     (if (contains? acc val) 
                       (disj acc val)
                       ((fnil conj #{}) acc val)))])))

; Reason for `""`: https://zhenyong.github.io/react/tips/controlled-input-null-value.html
(defn attrs-value [attrs stored-val]
  (assoc attrs :value (or @stored-val "")))

; -----------------------------------------------------------------------------
; Input Components
; -----------------------------------------------------------------------------

(defmulti input :type)

; text, email, password
(defmethod input :default
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs (-> attrs 
                         (attrs-on-change-set target-value)
                         (attrs-value stored-val))]
    [:input edited-attrs]))

(defmethod input :number
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs (-> attrs
                         (attrs-on-change-set (comp format-number target-value))
                         (attrs-value stored-val))]
    [:input edited-attrs]))

(defn textarea 
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs (-> attrs
                         (attrs-on-change-set target-value)
                         (attrs-value stored-val))]
    [:textarea edited-attrs]))

(defmethod input :radio
  [attrs]
  (let [{:keys [name value]} attrs
        stored-val (get-stored-val name)
        edited-attrs (-> attrs
                         (attrs-on-change-set (comp read-string* target-value))
                         (assoc :checked (= value @stored-val)))]
    [:input edited-attrs]))
                          
((defmethod input :checkbox
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs (-> attrs
                         (attrs-on-change-update not)
                         (assoc :checked @stored-val))]
    [:input edited-attrs])))

(defn select 
  [attrs options]
  (let [{:keys [name multiple]} attrs
        stored-val (get-stored-val (:name attrs))
        on-change-fn (if multiple attrs-on-change-multiple attrs-on-change-set)
        edited-attrs (-> attrs
                         (on-change-fn (comp read-string* target-value))
                         (attrs-value stored-val))]
    [:selected edited-attrs
     options]))


(def datetime-format "yyyy-mm-ddT03:00:00.000Z")

; NOTE: REQUIRES 
; "https://cdnjs.cloudflare.com/ajax/libs/bootstrap-datepicker/1.7.1/css/bootstrap-datepicker.min.css" 
(defn datepicker
  [attrs]
  (r/create-class
    {:display-name "datepicker component"
     
     :reagent-render
     (fn [attrs]
       (let [edited-attrs (-> attrs
                              (assoc :type :text)
                              (update :class str " form-control"))]
        [:div.input-group.date
         [input edited-attrs]
         [:div.input-group-addon
          [:i.glyphicon.glyphicon-calendar]]]))
     
     :component-did-mount
     (fn [this]
       (.datepicker (js/$ (r/dom-node this))
                    (clj->js {:format (or (:format attrs) 
                                          datetime-format)}))
       (-> (.datepicker (js/$ (r/dom-node this)))
           (.on "changeDate"
                #(let [d (.datepicker (js/$ (r/dom-node this))
                                      "getDate")]
                   (rf/dispatch [:set (:name attrs)
                                 (.getTime d)])))))}))
               

; file