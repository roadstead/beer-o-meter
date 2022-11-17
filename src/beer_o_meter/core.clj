(ns beer-o-meter.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]))

(def host "https://www52.v1host.com/skatteministeriet")
(def api "/rest-1.v1")
(def data-api "/Data")
(def auth-header {:Authorization "Basic S2V2aW4gRmxhaGVydHk6aXJpc2g0NQ=="})

(defn query-data-api [query]
  (-> (str host api data-api query)
      (client/get (merge {:headers auth-header} {:accept :json}))
      :body
      (json/decode true)))

(defn get-defects [workitem team]
    (-> (str "/Scope/688178/Workitems:" workitem "[Team.Name='" team "']")
        query-data-api ))

(defn get-defect [oid]
  (-> (str "/Defect/" oid)
      (query-data-api)))

(defn get-resolution-date [project-name]
  (-> (str 
       "/Defect?where=Scope.ParentMeAndUp.Name='" 
       project-name 
       "'&sel=Name,Status,History[Status.Name='Future','In%20Progress','Done'].Days.@Sum")
      query-data-api ))



