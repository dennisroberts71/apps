(ns apps.tools
  (:use [apps.containers :only [add-tool-container set-tool-container tool-container-info]]
        [apps.persistence.entities :only [tools]]
        [apps.util.conversions :only [remove-nil-vals]]
        [apps.validation :only [verify-tool-name-location validate-tool-not-used]]
        [korma.core :exclude [update]]
        [korma.db :only [transaction]]
        [slingshot.slingshot :only [try+]])
  (:require [apps.clients.permissions :as perms-client]
            [apps.persistence.tools :as persistence]
            [apps.tools.permissions :as permissions]
            [clojure.tools.logging :as log]))

(defn format-tool-listing
  [perms public-tool-ids {:keys [id] :as tool}]
  (-> tool
      (assoc :is_public  (contains? public-tool-ids id)
             :permission (or (perms id) ""))
      remove-nil-vals))

(defn search-tools
  "Obtains a listing of tools for the tool search service."
  [{:keys [user] :as params}]
  (let [perms           (perms-client/load-tool-permissions user)
        tool-ids        (set (keys perms))
        public-tool-ids (perms-client/get-public-tool-ids)]
    {:tools
     (map (partial format-tool-listing perms public-tool-ids)
          (persistence/get-tool-listing (assoc params :tool-ids tool-ids)))}))

(defn get-tool
  "Obtains a tool by ID."
  [user tool-id]
  (permissions/check-tool-permissions user "read" [tool-id])
  (let [tool           (->> (persistence/get-tool tool-id)
                            (format-tool-listing (perms-client/load-tool-permissions user)
                                                 (perms-client/get-public-tool-ids)))
        container      (tool-container-info tool-id)
        implementation (persistence/get-tool-implementation-details tool-id)]
    (assoc tool
      :container container
      :implementation implementation)))

(defn- add-new-tool
  [{:keys [container] :as tool}]
  (verify-tool-name-location tool)
  (let [tool-id (persistence/add-tool tool)]
    (when container
      (add-tool-container tool-id container))
    tool-id))

(defn add-tools
  "Adds a list of tools to the database, returning a list of IDs of the tools added."
  [{:keys [tools]}]
  (transaction
    (let [tool-ids (doall (map add-new-tool tools))]
      (dorun (map perms-client/register-public-tool tool-ids))
      {:tool_ids tool-ids})))

(defn update-tool
  [user overwrite-public {:keys [id container] :as tool}]
  (persistence/update-tool tool)
  (when container
    (set-tool-container id overwrite-public container))
  (get-tool user id))

(defn delete-tool
  [user tool-id]
  (let [{:keys [name location version]} (get-tool user tool-id)]
    (validate-tool-not-used tool-id)
    (log/warn user "deleting tool" tool-id name version "@" location))
  (delete tools (where {:id tool-id}))
  (try+
    (perms-client/delete-tool-resource tool-id)
    (catch [:status 404] _
      (log/warn "tool resource" tool-id "not found by permissions service")))
  nil)
