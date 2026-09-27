import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ChatResponse } from '../../core/models';
import { AssistantStore } from './assistant.store';

describe('AssistantStore', () => {
  let store: AssistantStore;
  let backend: HttpTestingController;

  const reply = (over: Partial<ChatResponse> = {}): ChatResponse => ({
    conversationId: 'conv-1', userId: 'NT10036', reply: 'Would you like me to submit the missing access requests?',
    awaitingConfirmation: true, analysis: null, accessRequest: null, toolCalls: [], ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(AssistantStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sends the message, keeps the conversation id and offers confirmation', () => {
    store.send('I just joined the Novatech project');
    const first = backend.expectOne('/api/agent/chat');
    expect(first.request.body).toEqual({ message: 'I just joined the Novatech project', conversationId: null });
    expect(store.pending()).toBeTrue();
    first.flush(reply());

    expect(store.messages().map(m => m.role)).toEqual(['user', 'assistant']);
    expect(store.awaitingConfirmation()).toBeTrue();

    store.send('Yes');
    const second = backend.expectOne('/api/agent/chat');
    expect(second.request.body.conversationId).toBe('conv-1');
    second.flush(reply({ awaitingConfirmation: false }));
    expect(store.awaitingConfirmation()).toBeFalse();
  });

  it('sends the user selection with a confirmation and exposes the open offer', () => {
    store.send('I joined Novatech');
    backend.expectOne('/api/agent/chat').flush(reply({
      analysis: {
        userId: 'NT10036', userName: 'John', role: 'Developer', projectId: 1, projectName: 'Novatech',
        alreadyHave: [], missing: [], alreadyRequested: [], requestableEntitlementIds: [4, 3, 5],
        verification: '', nextStep: '',
      },
    }));
    expect(store.openOffer()?.analysis.requestableEntitlementIds).toEqual([4, 3, 5]);

    store.send('Yes, submit only the selected access: NOVATECH_VPN', [5]);
    const req = backend.expectOne('/api/agent/chat');
    expect(req.request.body.selectedEntitlementIds).toEqual([5]);
    req.flush(reply({ awaitingConfirmation: false }));
    expect(store.openOffer()).toBeNull();
  });

  it('shows a single banner when the AI model is not configured', () => {
    store.send('hello');
    backend.expectOne('/api/agent/chat').flush(
      { title: 'AI agent not configured', detail: 'set OPENAI_API_KEY' },
      { status: 503, statusText: 'Service Unavailable' });

    expect(store.notConfigured()).toBe('set OPENAI_API_KEY');
    expect(store.messages().map(m => m.role)).toEqual(['user']);
    expect(store.pending()).toBeFalse();
  });

  it('shows other errors in the thread', () => {
    store.send('hello');
    backend.expectOne('/api/agent/chat').flush({ detail: 'The AI provider call failed' },
      { status: 502, statusText: 'Bad Gateway' });
    expect(store.messages().at(-1)).toEqual(jasmine.objectContaining({ role: 'error', text: 'The AI provider call failed' }));
  });

  it('ignores blank messages and resets the conversation', () => {
    store.send('   ');
    backend.expectNone('/api/agent/chat');

    store.send('hi');
    backend.expectOne('/api/agent/chat').flush(reply());
    store.reset();
    expect(store.messages()).toEqual([]);
  });
});
