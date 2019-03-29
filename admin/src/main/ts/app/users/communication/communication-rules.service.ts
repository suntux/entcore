import { Injectable } from '@angular/core';
import { CommunicationRule } from './communication-rules.component';
import { GroupModel, InternalCommunicationRule } from '../../core/store/models';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/do';
import 'rxjs/add/observable/forkJoin';

@Injectable()
export class CommunicationRulesService {

    private rulesSubject: Subject<BidirectionalCommunicationRules> = new Subject<BidirectionalCommunicationRules>();
    private rulesObservable: Observable<BidirectionalCommunicationRules> = this.rulesSubject.asObservable();
    private currentRules: BidirectionalCommunicationRules;

    constructor(private http: HttpClient) {
        this.rulesObservable.subscribe(cr => this.currentRules = cr);
    }

    public changes(): Observable<BidirectionalCommunicationRules> {
        return this.rulesObservable;
    }

    public setGroups(groups: GroupModel[]) {
        Observable.forkJoin(
            Observable.forkJoin(...groups.map(group => this.getSendingCommunicationRulesOfGroup(group))),
            Observable.forkJoin(...groups.map(group => this.getReceivingCommunicationRulesOfGroup(group)))
                .map(arr => arr.reduce((prev, current) => {
                    prev.push(...current);
                    return prev;
                }, []))
        )
            .map(arr => ({sending: arr[0], receiving: arr[1]}))
            .subscribe((communicationRules: BidirectionalCommunicationRules) => this.rulesSubject.next(communicationRules));
    }

    public toggleInternalCommunicationRule(group: GroupModel): Observable<InternalCommunicationRule> {
        let request: Observable<{ users: InternalCommunicationRule | null }>;
        const direction: InternalCommunicationRule = group.internalCommunicationRule === 'BOTH' ? 'NONE' : 'BOTH';

        if (direction === 'BOTH') {
            request = this.http.post<{ users: InternalCommunicationRule | null }>(`/communication/group/${group.id}/users`, null);
        } else {
            request = this.http.delete<{ users: InternalCommunicationRule | null }>(`/communication/group/${group.id}/users`);
        }

        return request
            .map(resp => resp.users ? resp.users : 'NONE')
            .do((internalCommunicationRule) => {
                if (this.currentRules) {
                    const groupInCommunicationRules = this.findGroupInCommunicationsRule(this.currentRules.sending, group.id);
                    if (!!groupInCommunicationRules) {
                        groupInCommunicationRules.internalCommunicationRule = internalCommunicationRule;
                        this.rulesSubject.next(this.clone(this.currentRules));
                    }
                }
            });
    }

    public removeCommunication(sender: GroupModel, receiver: GroupModel): Observable<void> {
        return this.http.delete<void>(`/communication/group/${sender.id}/communique/${receiver.id}`)
            .do(() => {
                if (this.currentRules) {
                    const communicationRuleOfSender = this.currentRules.sending.find(cr => cr.sender.id === sender.id);
                    if (!!communicationRuleOfSender) {
                        communicationRuleOfSender.receivers = communicationRuleOfSender.receivers
                            .filter(r => r.id !== receiver.id);
                        this.rulesSubject.next(this.clone(this.currentRules));
                    }
                }
            });
    }

    private getSendingCommunicationRulesOfGroup(sender: GroupModel): Observable<CommunicationRule> {
        return this.http.get<GroupModel[]>(`/communication/group/${sender.id}/outgoing`)
            .map(receivers => ({sender, receivers}));
    }

    private getReceivingCommunicationRulesOfGroup(receiver: GroupModel): Observable<CommunicationRule[]> {
        return this.http.get<GroupModel[]>(`/communication/group/${receiver.id}/incoming`)
            .map((senders: GroupModel[]): CommunicationRule[] => senders.map(sender => ({
                sender,
                receivers: [receiver]
            })))
    }

    private findGroupInCommunicationsRule(communicationRules: CommunicationRule[], groupId: string): GroupModel {
        return communicationRules.reduce(
            (arrayOfGroups: GroupModel[], communicationRule: CommunicationRule): GroupModel[] =>
                [...arrayOfGroups, communicationRule.sender, ...communicationRule.receivers], [])
            .find(group => group.id === groupId);
    }

    private clone(bidirectionalCommunicationRules: BidirectionalCommunicationRules): BidirectionalCommunicationRules {
        return {
            sending: this.cloneCommunicationRules(bidirectionalCommunicationRules.sending),
            receiving: this.cloneCommunicationRules(bidirectionalCommunicationRules.receiving)
        }
    }

    private cloneCommunicationRules(communicationRules: CommunicationRule[]): CommunicationRule[] {
        return communicationRules.map(cr => ({
            sender: Object.assign({}, cr.sender),
            receivers: cr.receivers.map(re => Object.assign({}, re))
        }));
    }
}

export interface BidirectionalCommunicationRules {
    sending: CommunicationRule[],
    receiving: CommunicationRule[]
}
