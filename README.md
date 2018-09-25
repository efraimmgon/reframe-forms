# reframe-forms

A ClojureScript library to provide form data bindings for [re-frame](https://github.com/Day8/re-frame)

## Install

[reframe-forms "0.1.0-SNAPSHOT"]

## Usage

`input` creates an input tag based on the :type key.

Data binding occurs based on the `:name` key. The name key is destructured and used as a location in the app-db. `:blog.post/title`, for instance, will  point to `{:blog {:post {:title input-data-here}}}`.

The `:radio` input requires a `:value` attribute, and the `textarea`, `select`, and the `datepicker` tags are defined as a separate functions.

You can pass a `:format` (string) attribute for the `datepicker` input, representing the display format. The date value is stored as a timestamp int.

The `select` input takes an optional `:multiple` attribute. If it is truthy, select will select one or more values (into a set), and if false it will select a single option. As a second parameter `select` takes a coll of options with a `:value` attribute:

```clojure
[select {:name :user/country, :multiple true}
    [:option {:value "Brazil"}]
    [:option {:value "United States of America"}]
    [:option {:value "Portugal"}]]
```

To set default values for the inputs you must use re-frame to set a value to the location of the :name attribute of the input. For instance, in the previous example, if we want `select` to have "Portugal" as its default value, we must set the :country key somehow,

```clojure
(rf/dispatch [:set [:user :country] #{"Portugal"}])
```

Here is an example usage of several input types:

```clojure
(ns my-website.blog
  (:require
   [my-website.components :refer [card form-group]]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [reframe-forms.core :refer [input textarea datepicker]]))

(defn blog-post-form []
  [:div 
   [form-group
     "Title *"
     [input {:type :text
             :name :blog.post/title
             :class "form-control"}]]
   [form-group
     "Status *"
     [:label.form-check-label
       [input {:type :radio
               :name :blog.post/status
               :value "draft"
               :class "form-check-input"}]
       " Draft"]
     [:label.form-check-label
      [input {:type :radio
              :name :blog.post/status
              :value "published"
              :class "form-check-input"}]
      " Published"]]
   [form-group
     "Content *"
     [textarea {:name :blog.post/content
                :class "form-control"}]]
   [form-group
     "Published at"
     [datepicker {:name :blog.post/published-at}]]])

; Create the UI for the user and handle the forms data in the
; usual re-frame way.
(defn create-post-panel []
  (r/with-let [fields (rf/subscribe [:query :blog/post])]
    [:div.row>div.col-md-12
     [card
      {:title
       [:div
        "New post"
         [:div.pull-right
          [:button.btn.btn-primary
           {:on-click #(rf/dispatch [:blog/create-post fields])}
           "Save post"]]]
       :content
       [:div
        [post-form-template fields]]}]]))

```



## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
