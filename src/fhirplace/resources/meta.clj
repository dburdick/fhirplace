(ns ^{:doc "meta information about FHIR resources
           all queries go here"}
  fhirplace.resources.meta
  (:require
    [clojure.string :as string]
    [clojure.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip.xml :as xx]
    [clojure.java.io :as io]))

(import 'java.io.File)

(defn- attr
  "get attr"
  [n attr]
  (get-in n [:attrs attr]))

(defn- zattr
  "construct get attr function"
  [loc a]
  (attr (zip/node loc) a))

(defn- xml->val [loc & path]
  (if-let [node (apply xx/xml1-> loc path)]
    (zattr node :value)))

(defn- resource
  "return zipper loc"
  [db res-type]
  (xx/xml1-> db :entry :content :Profile :structure
             #(= (xml->val % :type) res-type)))

(defprotocol FHIRPath
  (child? [parent child] "test if p1 is child of p2")
  (basename [path] "get last item in path")
  (root [path] "get first item in path")
  (join [path item] "add new item to path"))

(extend-type String
  FHIRPath
  (child? [p c]
    (let [p (string/split p #"\.")
          c (string/split c #"\.")]
      (= p (butlast c))))
  (basename [path]
    (last (string/split path #"\.")))
  (root [path]
    (first (string/split path #"\.")))
  (join [path item]
    (str path "." (name item))))

(defn- mk-elem [loc]
  (let [path (xml->val loc :path)]
    {:path  path
     :name (basename path)
     :min  (xml->val loc :definition :min)
     :max  (xml->val loc :definition :max)
     :type (xml->val loc :definition :type :code) }))

(defn- elem-children* [res-loc path]
  (xx/xml-> res-loc :element
            #(child? path (xml->val % :path))))

(defn- load-profile [path]
  (->
    (io/resource path)
    (.toURI)
    (File.)
    (xml/parse)
    (zip/xml-zip)))

(def ^{:private true} res-profile
  (load-profile "fhir/profiles-resources.xml"))

(def ^{:private true} dt-profile
  (load-profile "fhir/profiles-types.xml"))

(defn is-complex? [type-name]
  (Character/isUpperCase (first type-name)))

(defn poly-attr? [attr-name]
  (not (nil? (re-find #"\[x\]$" (str attr-name)))))

;; bad function
(defn poly-keys-match? [key attr-name]
  (let [prefix (string/replace (name key) #"\[x\]$" "")
        key-re  (re-pattern (str "^" prefix))]
    (not (nil? (re-find key-re (name attr-name))))))

;;TODO: rename into emels
(defn elem-children
  [path]
  (let [res-type (root path)
        res-loc  (or (resource res-profile res-type)
                     (resource dt-profile res-type))]
    (if res-loc
      (map
        mk-elem
        (elem-children* res-loc path))
      (throw (Exception. (str "could not find meta for " path))))))