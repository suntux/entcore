import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core'
import { ActivatedRoute, Data, Router, NavigationEnd } from '@angular/router'
import { Subscription } from 'rxjs/Subscription'
import { routing } from '../core/services/routing.service'

@Component({
    selector: 'imports-exports-root',
    template: `
        <div class="flex-header">
            <h1><i class="fa fa-exchange"></i> {{ 'imports.exports' | translate }}</h1>
        </div>
        
        <div class="tabs">
            <button class="tab" *ngFor="let tab of tabs"
                [disabled]="tab.disabled"
                [routerLink]="tab.view"
                routerLinkActive="active">
                {{ tab.label | translate }}
            </button>
        </div>

        <router-outlet></router-outlet>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportsExportsRoot implements OnInit, OnDestroy {

     // Subscriberts
    private structureSubscriber: Subscription

    // Tabs
    tabs = [
        { label: "import.users", view: "import-csv"},
        { label: "export.accounts", view: "export" },
        { label: "massmail.accounts", view: "massmail" } // Meld MassMail into export ?
    ]

    private routerSubscriber : Subscription
    private error: Error

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private cdRef: ChangeDetectorRef) { }

    ngOnInit(): void {
        // Watch selected structure
        this.structureSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            if(data['structure']) {
                this.cdRef.markForCheck()
            }
        })

        this.routerSubscriber = this.router.events.subscribe(e => {
            if(e instanceof NavigationEnd)
                this.cdRef.markForCheck()
        })
    }

    ngOnDestroy(): void {
        this.structureSubscriber.unsubscribe()
        this.routerSubscriber.unsubscribe()
    }

    onError(error: Error){
        console.error(error)
        this.error = error
    }
}
