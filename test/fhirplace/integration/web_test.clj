(ns fhirplace.integration.web-test
  (:use midje.sweet)
  (:require [ring.util.request :as request]
            [ring.util.response :as response]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [fhirplace.test-helper :refer :all]))


(defn json-body [response]
  (json/read-str (:body response) :key-fn keyword))

(def patient-json-str (fixture-str "patient"))
(def patient-json (fixture "patient"))

(deffacts "FHIRPlace respond to /info with debug info"
  (fact
    (GET "/info") =not=> nil))

(deffacts "About /metadata"
  (let [resp (GET "/metadata")
        conf (json-body resp)]

    (fact "respond with not-empty body"
          resp =not=> nil)

    (fact "respond with 200 HTTP status"
          (:status resp) => 200)

    (fact "returns Conformance resource"
          (:resourceType conf) => "Conformance")

    (future-fact "when requesting with mime-type=application/xml returns resource as XML"
                 (:body (GET "/metadata" {:_format "application/xml"})) => #"\<\?xml version='1\.0' encoding='UTF-8'\?\>")

    (fact "Conformance resource contains :rest key with all available resources"
          (get-in conf [:rest 0 :resources])=> #(< 0 (count %)))))


(comment
  (deffacts "About READing non-existent resource"
    (let [response (GET (str "/patient/" (make-uuid)))]
      (:status response) => 404))

  (deffacts "About HISTORY"
    (let [create-response (POST "/Patient" patient-json-str)
          resource-loc-with-history (response/get-header create-response "Location")
          resource-loc (string/replace resource-loc-with-history #"_history/.+" "_history")
          resource-loc-simple (string/replace resource-loc-with-history #"/_history/.+" "")]

      (fact "get history"
            (GET resource-loc) => (contains {:status 200})
            (json-body (GET resource-loc)) => (contains {:resourceType "Bundle"
                                                         :entry anything}))

      (fact "get history with _count and _since"
            (let [update-body (json/write-str
                                (update-in  patient-json [:telecom] conj
                                           {:system "phone"
                                            :value "+919191282"
                                            :use "home"} ))
                  update-response (PUT-LONG resource-loc-simple update-body {"Content-Location" resource-loc-with-history})
                  update-last-modified (response/get-header update-response "Last-Modified")]
              (:status update-response) => 200
              (count (:entry (json-body (GET resource-loc)))) => 2
              (count (:entry (json-body (GET (str resource-loc "?_count=1"))))) => 1
              (count (:entry (json-body (GET (str resource-loc (str "?_since=" (ring.util.codec/url-encode update-last-modified))))))) => 1))))

  (deffacts "About VALIDATE"
    (let [valid-response (POST "/Patient/_validate" patient-json-str)
          invalid-response (POST "/Patient/_validate" (fixture-str "invalid-patient"))
          broken-response (POST "/Patient/_validate" "hi there i'm invalid json lol")]

      (fact "when validating valid resource should respond with 200"
            (:body valid-response) => ""
            (:status valid-response) => 200)

      (fact "when validating invalid resource should respond with 422"
            (:body invalid-response) => nil
            (:status invalid-response) => 422)

      (fact "when received request with broken body should respond with 400"
            (get-in
              (json-body broken-response)
              [:issue 0 :details]) => #"Request body"

            (:status broken-response) => 400))))
