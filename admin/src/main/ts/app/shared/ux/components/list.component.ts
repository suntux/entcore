import { Component, Input, Output, ChangeDetectionStrategy, ChangeDetectorRef, EventEmitter, 
    TemplateRef, ContentChild } from '@angular/core'
import { Subject } from 'rxjs/Subject'

@Component({
    selector: 'list',
    template: `
        <search-input [attr.placeholder]="searchPlaceholder | translate" (onChange)="inputChange.emit($event)"></search-input>
        <div class="toolbar">
            <ng-content select="[toolbar]"></ng-content>
        </div>
        <div class="list-wrapper"
            infiniteScroll
            [scrollWindow]="false"
            (scrolled)="scrolledDown.emit()"
            [infiniteScrollThrottle]="50">
            <ul>
                <li *ngFor="let item of model | filter: filters | filter: inputFilter | store:self:'storedElements' | orderBy: sort | slice: 0:limit"
                    (click)="onSelect.emit(item)"
                    [class.selected]="isSelected(item)"
                    [class.disabled]="isDisabled(item)"
                    [ngClass]="ngClass(item)">
                    <ng-template [ngTemplateOutlet]="templateRef" [ngOutletContext]="{$implicit: item}">
                    </ng-template>
                </li>
            </ul>
            <ul *ngIf="storedElements && storedElements.length === 0">
                <li class="no-results">{{ noResultsLabel | translate }}</li>
            </ul>
        </div>
    `,
    styles: [`
        ul {
            margin: 0;
            padding: 0px;
            font-size: 0.9em;
        }

        ul li {
            cursor: pointer;
            border-top: 1px solid #ddd;
            padding: 10px 10px;
        }

        ul li.disabled {
            pointer-events: none;
        }
    `],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ListComponent {

    /* Store pipe */
    self = this
    _storedElements = []

    constructor(
        public cdRef: ChangeDetectorRef){}

    @Input() model = []
    @Input() filters
    @Input() inputFilter
    @Input() sort
    @Input() limit: number
    @Input() searchPlaceholder = "search"
    @Input() isSelected = () => false
    @Input() isDisabled = () => false
    @Input() ngClass = () => ({})
    @Input() noResultsLabel = "list.results.no.items"
    
    @Output() inputChange: EventEmitter<string> = new EventEmitter<string>()
    @Output() onSelect: EventEmitter<{}> = new EventEmitter()
    @Output() listChange: EventEmitter<any> = new EventEmitter()
    @Output() scrolledDown: EventEmitter<{}> = new EventEmitter()

    @ContentChild(TemplateRef) templateRef:TemplateRef<any>;

    set storedElements(list) {
        this._storedElements = list
        this.listChange.emit(list)
    }

    get storedElements() {
        return this._storedElements
    }
}