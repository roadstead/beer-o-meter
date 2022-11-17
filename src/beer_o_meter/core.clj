(ns beer-o-meter.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]))

(def host "https://www52.v1host.com/skatteministeriet")
(def api "/rest-1.v1")
(def data-api "/Data")

(defn query-data-api [query]
  (-> (str host api data-api query)
      (client/get (merge {:headers auth-header} {:accept :json}))
      :body
      (json/decode true)))

(defn get-scopes 
  ([]
   (let [default-project "ICE Solution Train (from PI-20)"]
     (get-scopes default-project)))
  ([project]
   (let [base-scope (str "/Scope?where=Scope.ParentMeAndUp.Name='" project "'")]
     (-> base-scope
         query-data-api
         :Assets))))

(defn get-assets 
  ([workitem team]
   (let [pi-38 "688178"]
     (get-assets pi-38 workitem team)))
  ([scope-id workitem team]
   (-> (str "/Scope/" scope-id "/Workitems:" workitem "[Team.Name='" team "']")
       query-data-api
       :value)))

(defn get-oid [asset]
  (:idref asset))

(defn id->workasset-oid [id]
  (clojure.string/split id #":"))

(defn get-asset 
  ([idref]
   (-> idref
       id->workasset-oid
       ((fn [[workitem oid]] (get-asset workitem oid)))))
  ([workitem oid]
   (-> (str "/" workitem "/" oid)
       (query-data-api))))

(defn get-resolution-date [project-name]
  (-> (str 
       "/Defect?where=Scope.ParentMeAndUp.Name='" 
       project-name 
       "'&sel=Name,Status,History[Status.Name='Future','In%20Progress','Done'].Days.@Sum")
      query-data-api))

(defn calculate-duration [{:keys [start end]}]
  (let [inst-pair (->> [start end]
                       (mapv #(clojure.instant/read-instant-date %))
                       (mapv #(.toInstant %)))]
    (-> (java.time.Duration/between (first inst-pair) (second inst-pair))
        .getSeconds)))

(defn parse-duration [d]
  {:start (get-in d [:Attributes :CreateDate :value])
   :end   (get-in d [:Attributes :ClosedDate :value])})

(defn get-duration 
  ([idref]
   (-> idref
       id->workasset-oid
       ((fn [[workitem oid]] (get-duration workitem oid)))))
  ([workitem-type oid]
     (-> (str "/" workitem-type "/" oid "?sel=CreateDate,ClosedDate")
         query-data-api
         parse-duration)))

(defn ->closed [durations]
  (->> durations
       (remove #(nil? (:end %)))))

(defn ->mttf [durations]
  (->> durations
       (mapv calculate-duration)
       (reduce +)
       ((fn [d] (if (> (count durations) 0)
                  (/ d (count durations))
                  0)))
       float))

(defn seconds->days [s]
  (-> s
      (/ 60 60 24)
      bigdec))

(defn dec->str [d]
  (format "%.2f" d))

(defn compute-mttf [assets]
  (->> assets
       (mapv get-oid)
       (mapv get-duration)
       ->closed
       ->mttf
       seconds->days
       dec->str))

(defn filter-projects [regex projects]
  (->> projects
       (mapv #(select-keys % [:id :Attributes]))
       (mapv #(update-in % [:Attributes] (fn [attr] (get-in attr [:Name :value]))))
       (filter #(re-matches regex (:Attributes %)))))

(defn get-assets-for-team 
  ([workitem team]
   (get-assets-for-team (get-scopes) workitem team))
  ([all-projects workitem team]
   (->> all-projects
        (filter-projects #".*PI-3.*")
        (mapv #(assoc {} 
                      :project (:Attributes %)
                      :team    team
                      :assets  (get-assets (second (id->workasset-oid (:id %))) workitem team))))))

(comment 
  (def auth-header {:Authorization "Basic foobar"}) ;;define me with a base64 encoded user:password

  (def spr-defects (get-assets "Defect" "Team Sagsproces"))
  (def spr-durations (->> spr-defects
                          (mapv get-oid)
                          (mapv get-duration)))
  (def cyan-defects (get-assets "Defect" "Team Cyan"))
  (def cyan-durations (->> cyan-defects
                           (mapv get-oid)
                           (mapv get-duration)))
  
  (->> cyan-durations
       ->closed
       ->mttf
       seconds->days
       dec->str)

  (compute-mttf spr-defects)

  (def base-scopes (get-scopes))

  (->> base-scopes
       (mapv :id)
       (mapv id->workasset-oid)
       (mapv #(get-assets (second %) "Defect" "Team Cyan"))
       (map count))

  ;;Get all projects
  (->> (get-scopes)
       (mapv #(select-keys % [:id :Attributes]))
       (mapv #(update-in % [:Attributes] (fn [attr] (get-in attr [:Name :value])))))

  )
