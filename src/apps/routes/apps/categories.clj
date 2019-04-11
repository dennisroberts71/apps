(ns apps.routes.apps.categories
  (:use [common-swagger-api.routes]
        [common-swagger-api.schema]
        [common-swagger-api.schema.apps
         :only [AppCategoryIdPathParam
                AppListing
                SystemId]]
        [common-swagger-api.schema.ontologies :only [OntologyClassIRIParam]]
        [apps.routes.params :only [SecuredQueryParams]]
        [apps.routes.schemas.app :only [AppListingPagingParams]]
        [apps.routes.schemas.app.category
         :only [AppCommunityGroupNameParam
                CategoryListingParams
                OntologyAppListingPagingParams
                OntologyHierarchyFilterParams]]
        [apps.user :only [current-user]]
        [apps.util.coercions :only [coerce!]]
        [ring.util.http-response :only [ok]])
  (:require [apps.service.apps :as apps]
            [apps.service.apps.de.listings :as listings]
            [apps.util.service :as service]
            [common-swagger-api.schema.apps.categories :as schema]
            [compojure.route :as route]))

(defroutes app-categories
  (GET "/" []
        :query [params CategoryListingParams]
        :return schema/AppCategoryListing
        :summary schema/AppCategoryListingSummary
        :description schema/AppCategoryListingDocs
        (ok (apps/get-app-categories current-user params)))

  (GET "/:system-id/:category-id" []
        :path-params [system-id :- SystemId
                      category-id :- AppCategoryIdPathParam]
        :query [params AppListingPagingParams]
        :return schema/AppCategoryAppListing
        :summary schema/AppCategoryAppListingSummary
        :description schema/AppCategoryAppListingDocs
        (ok (coerce! schema/AppCategoryAppListing
                 (apps/list-apps-in-category current-user system-id category-id params))))

  (undocumented (route/not-found (service/unrecognized-path-response))))

(defroutes app-hierarchies

  (GET "/" []
       :query [params SecuredQueryParams]
       :summary schema/AppHierarchiesListingSummary
       :description-file "docs/apps/categories/hierarchies-listing.md"
       (listings/list-hierarchies current-user))

  (context "/:root-iri" []
    :path-params [root-iri :- OntologyClassIRIParam]

    (GET "/" []
         :query [{:keys [attr]} OntologyHierarchyFilterParams]
         :summary schema/AppCategoryHierarchyListingSummary
         :description-file "docs/apps/categories/category-hierarchy-listing.md"
         (listings/get-app-hierarchy current-user root-iri attr))

    (GET "/apps" []
         :query [{:keys [attr] :as params} OntologyAppListingPagingParams]
         :return AppListing
         :summary schema/AppCategoryAppListingSummary
         :description-file "docs/apps/categories/hierarchy-app-listing.md"
         (ok (coerce! AppListing (apps/list-apps-under-hierarchy current-user root-iri attr params))))

    (GET "/unclassified" []
         :query [{:keys [attr] :as params} OntologyAppListingPagingParams]
         :return AppListing
         :summary schema/AppHierarchyUnclassifiedListingSummary
         :description-file "docs/apps/categories/hierarchy-unclassified-app-listing.md"
         (ok (coerce! AppListing (listings/get-unclassified-app-listing current-user root-iri attr params)))))

  (undocumented (route/not-found (service/unrecognized-path-response))))

(defroutes app-communities

  (GET "/:community-id/apps" []
       :path-params [community-id :- AppCommunityGroupNameParam]
       :query [params AppListingPagingParams]
       :return AppListing
       :summary "List Apps in a Community"
       :description (str "Lists all of the apps under an App Community that are visible to the user."
                         (get-endpoint-delegate-block
                           "metadata"
                           "POST /avus/filter-targets"))
       (ok (coerce! AppListing (apps/list-apps-in-community current-user community-id params))))

  (undocumented (route/not-found (service/unrecognized-path-response))))
