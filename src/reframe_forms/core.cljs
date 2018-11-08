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

(defn clean-attrs [attrs]
  (dissoc attrs :save-fn
                :value-fn))
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

(defn on-change-set! 
  "Takes a path and a function and returns a handler.
  The function will be called on the DOM event object."
  [path f]
  (fn [event]
    (rf/dispatch [:reframe-forms/set path (f event)])))

(defn on-change-update! 
  "Takes a path and a function and returns a handler.
  The function will be called on the value stored at path."
  [path f]
  (fn [event]
    (rf/dispatch [:reframe-forms/update path f])))

(defn on-change-update-multiple! 
  "Takes a path and a value and returns a handler.
  The value will be disj'ed or conj'ed, depending if it is included or
  not at path."
  [path value]
  (fn [_]
    (let [f (fn [acc]
              (if (contains? acc val)
                (disj acc val)
                ((fnil conj #{}) acc val)))]
      (rf/dispatch [:reframe-forms/update path f]))))

; NOTE: Reason for `""`: https://zhenyong.github.io/react/tips/controlled-input-null-value.html
(defn value-attr [value]
  (or value ""))

; -----------------------------------------------------------------------------
; Input Components
; -----------------------------------------------------------------------------

(defmulti input :type)

; text, email, password
(defmethod input :default
  {:arglists '([{:keys [name]}])}
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs 
        (merge {:on-change (on-change-set! (:name attrs) target-value)
                :value (value-attr @stored-val)}
               attrs)]
    [:input edited-attrs]))

(defmethod input :number
  {:arglists '([{:keys [name]}])}
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs
        (merge {:on-change (on-change-set! (:name attrs) 
                                           (comp parse-number target-value))
                :value (value-attr @stored-val)}
               attrs)]
    [:input edited-attrs]))

(defn textarea
  {:arglists '([{:keys [name]}])}
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs
        (merge {:on-change (on-change-set! (:name attrs) target-value)
                :value (value-attr @stored-val)}
               attrs)]
    [:textarea edited-attrs]))

(defmethod input :radio
  {:arglists '([{:keys [name]}])}
  [attrs]
  (let [{:keys [name value]} attrs
        stored-val (get-stored-val name)
        edited-attrs
        (merge {:on-change (on-change-set! name
                                           (comp read-string* target-value))
                :checked (= value @stored-val)}
               attrs)]
    [:input edited-attrs]))
                          
(defmethod input :checkbox
  {:arglists '([{:keys [name]}])}
  [attrs]
  (let [stored-val (get-stored-val (:name attrs))
        edited-attrs
        (merge {:on-change (on-change-update! (:name attrs) not)
                :checked (boolean @stored-val)}
               attrs)]
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
  {:arglists '([{:keys [name save-fn value-fn]}])}
  [attrs]
  ; :value must be a string in the format "yyyy-mm-dd".
  (let [{:keys [name save-fn value-fn]
         :or   {save-fn to-iso-string,
                value-fn to-date-format}} 
        attrs
        stored-val (get-stored-val name)
        edited-attrs
        (merge {:on-change (on-change-set! name (comp save-fn target-value))
                :value (value-fn @stored-val)
                ;; If there's no browser support,
                ;; then at least we'll display the expected
                ;; format, and ...
                :placeholder "yyyy-mm-dd"
                ;; ... we'll display an error message if the wrong 
                ;; format is submitted.
                :pattern "[0-9]{4}-[0-9]{2}-[0-9]{2}"}
               (clean-attrs attrs))]
    [:input edited-attrs]))

(defn select 
  {:arglists '([{:keys [name multiple]}])}
  [attrs options]
  (let [{:keys [name multiple]} attrs
        stored-val (get-stored-val (:name attrs))
        on-change-fn! (if multiple on-change-update-multiple! on-change-set!)
        edited-attrs
        (merge {:on-change (on-change-fn! name (comp read-string* target-value))
                :value (value-attr @stored-val)}
               attrs)]
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