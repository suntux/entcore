import { $, appPrefix, ng } from 'entcore';
import { Subject } from 'rxjs';
//TODO move to infrafront? with i18n
function StickyDelegate(scope, element, attributes) {
    if (!attributes.stickyBloc) {
        return;
    }
    let original = null;
    let initialOffset = null;
    const dropZone = element.find(".dropzone-overlay");
    const parent = element.parent();
    const move = function () {
        if (original == null) {
            const position = dropZone.position()
            original = position.top;
            initialOffset = parent.scrollTop();
        }
        if (attributes.stickyBloc == "top") {
            const offset = parent.scrollTop();
            const newTop = original + offset - initialOffset;
            dropZone.css({ top: newTop + "px", "transition": "all 300ms ease-in-out" })
        }
    }
    const bind = function () {
        parent.scroll(move)
    }
    const unbind = function () {
        parent.off("scroll", move)
    }
    //
    bind();
    scope.$on("destroy", unbind)
}

function ResetScrollDelegate(scope: DropzoneOverlayScope, element, attributes) {
    if (!attributes.resetScroll) {
        return;
    }
    const parent = element.parent();
    let original = null;
    scope.onbeforeshow.subscribe(() => {
        if (original == null) {
            original = parent.css("overflow");
        }
        parent.css({ "overflow": "hidden" })
        parent.scrollTop(0);
    })
    scope.onhide.subscribe(() => {
        if (original != null) {
            parent.css({ "overflow": original })
        }
    })
}
//

export interface DropzoneOverlayScope {
    visible: boolean
    error: boolean
    canDrop: boolean
    onbeforeshow: Subject<any>
    onshow: Subject<any>
    onhide: Subject<any>
    displayError()
    showInfo(): boolean
    showWarning(): boolean
    show()
    hide()
    onImport(files: any)
    onCannotDrop()
    $on(a?, b?)
    $watch(a?, b?)
}
export const dropzoneOverlay = ng.directive('dropzoneOverlay', ['$timeout', ($timeout) => {
    return {
        restrict: 'E',
        scope: {
            onImport: '&',
            onCannotDrop: '&',
            canDrop: '=',
            activated: '='
        },
        template: `
                <div class="dropzone-overlay default flex-row center-component" ng-show="visible">
                <!--MESSAGE INFO-->
                <div class="default flex-row align-center message-box" ng-if="showInfo()">
                    <div class="twelve horizontal-spacing-twice vertical-spacing-four flex-row align-center">
                        <i class="horizontal-spacing cloud-upload primary-color"></i>
                        <em class=" flex-all-remains"><i18n>dropzone.overlay.label</i18n></em>
                    </div>
                </div>
                <!--MESSAGE ERROR-->
                <div class="default flex-row align-center message-box"  ng-if="showWarning()">
                    <div class="twelve horizontal-spacing-twice vertical-spacing-four flex-row align-center">
                        <i class="horizontal-spacing close warning-color"></i>
                        <em class=" flex-all-remains"><i18n>dropzone.overlay.warning</i18n><br/><i18n>dropzone.overlay.solution</i18n></em>
                        <button class="left-spacing no-margin-bottom" ng-click="hide()"><i18n><span class="no-style ng-scope"><i18n>dropzone.overlay.action</i18n></span></i18n></button>
                    </div>
                </div>
            </div>
        `,
        link: (scope: DropzoneOverlayScope, element, attributes) => {
            scope.onshow = new Subject;
            scope.onhide = new Subject;
            scope.onbeforeshow = new Subject
            //
            StickyDelegate(scope, element, attributes)
            ResetScrollDelegate(scope, element, attributes);
            const parent = element.parent();
            //
            scope.$watch("canDrop", function () {
                if (scope.canDrop === true) {
                    element.removeClass("cursor-notallowed")
                } else if (scope.canDrop === false) {
                    element.addClass("cursor-notallowed")
                }
            })
            scope.hide = function () {
                $timeout(() => {
                    scope.visible = false;
                    scope.error = false;
                    parent.removeClass('dropzone-overlay-wrapper');
                    scope.onhide.next()
                })
            }
            scope.show = function () {
                scope.onbeforeshow.next()
                $timeout(() => {
                    scope.visible = true;
                    parent.addClass('dropzone-overlay-wrapper');
                    scope.onshow.next()
                })
            }
            scope.showInfo = function () {
                return !scope.showWarning();
            }
            scope.showWarning = function () {
                return scope.error;
            }
            scope.displayError = function () {
                $timeout(() => {
                    scope.error = true;
                })
            }
            scope.error = false
            scope.hide();
            // 
            parent.on('dragenter', (e) => {
                scope.show()
            });

            parent.on('dragover', (e) => {
                e.preventDefault();
            });

            parent.on('dragleave', (event) => {
                //
                const rect = parent[0].getBoundingClientRect();
                const getXY = function getCursorPosition(event) {
                    let x, y;
                    if (typeof event.clientX === 'undefined') {
                        // try touch screen
                        x = event.pageX + document.documentElement.scrollLeft;
                        y = event.pageY + document.documentElement.scrollTop;
                    } else {
                        x = event.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
                        y = event.clientY + document.body.scrollTop + document.documentElement.scrollTop;
                    }
                    return { x: x, y: y };
                };
                const e = getXY(event.originalEvent);
                // Check the mouseEvent coordinates are outside of the rectangle
                if (e.x > rect.left + rect.width - 1 || e.x < rect.left || e.y > rect.top + rect.height - 1 || e.y < rect.top) {
                    scope.hide()
                }
            });

            parent.on('drop', async (e) => {
                e.preventDefault();
                if (scope.canDrop === false) {
                    scope.onCannotDrop && scope.onCannotDrop()
                    scope.hide();
                    return;
                }
                const files: FileList = e.originalEvent.dataTransfer.files;
                if (!files || !files.length) {
                    return;
                }
                //
                let valid = true;
                for (let i = 0, f; f = files[i]; i++) { // iterate in the files dropped
                    if (!f.type && f.size % 4096 == 0) {
                        //it looks like folder
                        valid = false;
                    }
                }
                //
                if (valid) {
                    scope.onImport({
                        '$event': files
                    });
                    scope.hide();
                } else {
                    scope.displayError();
                }
            });
            //
            scope.$on('$destroy', function () {
                parent.off()
                scope.onhide.unsubscribe()
                scope.onshow.unsubscribe()
            });
        }
    }
}])
