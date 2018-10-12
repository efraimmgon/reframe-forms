(ns reframe-forms.core
  (:require
   [cljs.reader :as reader]
   [clojure.string :as string]
   [reagent.core :as r]
   [re-frame.core :as rf]))

; Setting default value/checked: the idiomatic way of doing this is 
; setting the default value of the :name path to be the one you want
; to be the default.

; -----------------------------------------------------------------------------
; Input Components Utils
; -----------------------------------------------------------------------------

(defn get-stored-val [path]
  (rf/subscribe [:reframe-forms/query path]))

(defn target-value [event]
  (.-value (.-target event)))

(defn parse-number [string]
  (when-not (empty? string)
    (let [parsed (js/parseFloat string)]
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
  (assoc attrs :on-change #(rf/dispatch [:reframe-forms/set (:name attrs) (f %)])))

(defn attrs-on-change-update [attrs f]
  (assoc attrs :on-change #(rf/dispatch [:reframe-forms/update (:name attrs) f])))

(defn attrs-on-change-multiple [attrs val]
  (assoc attrs :on-change 
    #(rf/dispatch [:reframe-forms/update (:name attrs)
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
                         (attrs-on-change-set (comp parse-number target-value))
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
                          
(defmethod input :checkbox
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs (-> attrs
                         (attrs-on-change-update not)
                         (assoc :checked (boolean @stored-val)))]
    [:input edited-attrs]))

; Uses plain HTML5 <input type="date" />

(defn- to-timestamp 
  "Takes a string in the format 'yyyy-mm-dd' and returns a timestamp (int).
  If date-string is empty, returns nil."
  [date-string]
  (when-not (clojure.string/blank? date-string)
    (.getTime
      (js/Date. date-string))))

(defn- to-iso-string
  "Takes a string in the format 'yyyy-mm-dd' and returns a ISO date string.
  If date-string is empty, returns nil." 
  [date-string]
  (when-not (clojure.string/blank? date-string)
    (.toISOString
      (js/Date. date-string))))

(defn- to-date-format 
  "Takes a value that can be passed to js/Date. and retuns a string in 
  the format 'yyyy-mm-dd'. If x is nil, returns an empty string."
  [x]
  (if (nil? x)
    ""
    (-> (js/Date. x)
        .toISOString
        (clojure.string/split #"T")
        first)))
      
(defmethod input :date  
  [attrs]
  ; :value must be a string in the format "yyyy-mm-dd".
  (let [{:keys [name save-fn value-fn]
         :or {save-fn to-iso-string
              value-fn to-date-format}} attrs
        stored-val (get-stored-val name)
        edited-attrs (-> attrs
                         (attrs-on-change-set (comp save-fn target-value))
                         (assoc :value (value-fn @stored-val))
                         ;; If no browser support and it degrades to a text
                         ;; input, then at least we'll display the expected
                         ;; format, and ...
                         (update :placeholder #(or % "yyyy-mm-dd"))
                         ;; ... we'll display an error message the wrong 
                         ;; format is submitted.
                         (update :pattern #(or % "[0-9]{4}-[0-9]{2}-[0-9]{2}")))]
    [:input edited-attrs]))

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

;;; TODO: try Pickaday datepicker

(def datetime-format "yyyy-mm-ddT03:00:00.000Z")

; NOTE: only with Bootstrap 3
; NOTE: REQUIRES 
; "bootstrap.min.css"
; "bootstrap.min.js"
; "https://cdnjs.cloudflare.com/ajax/libs/bootstrap-datepicker/1.7.1/css/bootstrap-datepicker.min.css" 
; "https://cdnjs.cloudflare.com/ajax/libs/bootstrap-datepicker/1.7.1/js/bootstrap-datepicker.min.js"
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
                   (rf/dispatch [:reframe-forms/set (:name attrs)
                                 (.getTime d)])))))}))
               
; file