'use strict'

angular.module('fhirplaceGui')
  .controller 'ResourcesIndexCtrl', ($scope, $routeParams) ->
    $scope.resourceId    = $routeParams.resourceId
    $scope.resourceLabel = $routeParams.resourceId
