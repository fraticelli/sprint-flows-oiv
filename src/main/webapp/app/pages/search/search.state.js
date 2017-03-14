(function() {
    'use strict';

    angular
        .module('sprintApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider.state('search', {
            parent: 'app',
            url: '/search?',
            data: {
                authorities: ['ROLE_USER'] //todo: solo per ROLE_ADMIN?
            },
            views: {
                'content@': {
                    templateUrl: 'app/pages/search/search.html',
                    controller: 'SearchController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                mainTranslatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate,$translatePartialLoader) {
//todo: risolvere
//                    $translatePartialLoader.addPart('search');
//                    return $translate.refresh();
                }]
            },
        });
    }
})();
