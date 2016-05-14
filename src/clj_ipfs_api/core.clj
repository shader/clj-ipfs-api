(ns clj-ipfs-api.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [clojure.string :refer [join]])
  (:refer-clojure :exclude [get resolve update cat]))
           

(def ^:private default-url "http://127.0.0.1:5001")

(defn assemble-query
  "Assemble a map ready for request."
  [cmd-vec all-args]
  (let [{args true, [params] false} (group-by string? all-args)
        base-url                    (clojure.core/get (:request params)
                                                      :url
                                                      default-url)
        full-url                    (str base-url
                                         "/api/v0/"
                                         (join "/" cmd-vec))
        ipfs-params                 (dissoc params :request)]
    ; text for cat, json for everything else
    (assoc (merge {:as (if (= (last cmd-vec) "cat") :text :json)}
                  (:request params)) 
           :method :get
           :url full-url
           :query-params (if args
                             (assoc ipfs-params :arg args)
                             ipfs-params))))

(defn api-request
  "The same as used by clj-http."
  [raw-map]
  (let [json?       (= :json (:as raw-map)) ; Fiddle around to make it look the same as clj-http
        request-map (if json? (assoc raw-map :as :text) raw-map)
        {:keys [status headers body error]} @(http/request request-map)]
    (if error
        (println "Failed with exception: " error)
        (if json? (parse-string body true) body))))

; Bootstrapping using `ipfs commands`
(defn empty-fn
  "Template function used for generation."
  [cmd-vec]
  (fn [& args]
    (api-request (assemble-query cmd-vec args))))

(defn- unpack-cmds
  "Traverse the nested structure to get vectors of commands."
  [acc cmds]
  (mapcat (fn [{:keys [:Name :Subcommands]}]
            (if (empty? Subcommands)
                (list (conj acc Name))
                (unpack-cmds (conj acc Name) Subcommands)))
          cmds))

; Intern all the commands
(let [cmd-raw  ((empty-fn ["commands"]))
      cmd-vecs (unpack-cmds [] (:Subcommands cmd-raw))]
  (doseq [cmd-vec cmd-vecs]
    (intern *ns*
            (symbol (join "-" cmd-vec))
            (empty-fn cmd-vec))))
