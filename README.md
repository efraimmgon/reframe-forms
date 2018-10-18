# reframe-forms

A ClojureScript library to provide form data bindings for [re-frame](https://github.com/Day8/re-frame).

## Install

`[reframe-forms "0.1.0-SNAPSHOT"]`

## Usage

### IMPORTANT

Before anything else: our components rely on a set of `re-frame` events and subs to work.

(The subscription `:reframe-forms/query` to get values and the events `:reframe-forms/set` and `reframe-forms/update` to set/update values.)

You must either require `reframe-forms.events` to register the default events:

```clojure
(ns mywebsite.events
  (:require
    [mywebsite.db :as db]
    [reframe-forms.events] ; <-------
    [re-frame.core :as rf]))

  ...
```

Or you can come up with your own implementation, of course. In either case I suggest you take a look at the source code if you are curious (or confused) about our implementation. I'm no MIT graduate, so it's very simple code, really.

### Input types

The functionality rests on the `input` multimethod, and the `textarea` and `select` functions, at the `reframe-forms.core` namespace.

`input` creates an input tag based on the `:type` key.

Data binding occurs based on the `:name` value (either a keyword, or a vector of keys). It is used as a location in re-frame's `app-db`. `:blog.post/title` (or `[:blog :post :title]`), for instance, will  point to `{:blog {:post {:title ->input-data-here<-}}}`.

All the `input`s require only the `:name` value, with one exception: the `:radio` input requires a `:value` attribute.

The `select` input takes an optional `:multiple` attribute. If it is truthy, one or more values can be collected (into a set), and if falsey only one. As a second parameter `select` takes a coll of options with a `:value` attribute:

```clojure
[select {:name :user/country, :multiple true}
    [:option {:value "Brazil"}]
    [:option {:value "United States of America"}]
    [:option {:value "Portugal"}]]
```

For now, the way to set default values for the inputs one can set the value to the location of the :name attribute of the input. For instance, in the previous example, if we want `select` to have "Portugal" as its default value, we can set the :country key thusly:

```clojure
(rf/dispatch [:reframe-forms/set [:user :country] #{"Portugal"}])

;; Note that if :multiple is not set to true, we would set 
;; the default value for select differently:
(rf/dispatch [:reframe-forms/set [:user :country] "Portugal"])
```

Here is a usage example of several input types:

```clojure
; assume we have some helper components defined at my-website.components
(ns my-website.blog
  (:require
   [my-website.components :refer [card form-group]]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [reframe-forms.core :refer [input textarea]]))

(defn blog-post-form [fields]
  (when (nil? @fields)
    (rf/dispatch [:set :blog.post/status "published"]))
  (fn []
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
       [input {:type :date
               :class "form-control"
               :name :blog.post/published-at}]]]))

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
        [blog-post-form fields]]}]]))

```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
