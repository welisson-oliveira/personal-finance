import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AnomalyService } from './anomaly.service';

describe('AnomalyService', () => {
  let service: AnomalyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AnomalyService],
    });
    service = TestBed.inject(AnomalyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getAll should GET with includeResolved param', () => {
    service.getAll(true).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/anomalies');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('includeResolved')).toBe('true');
    req.flush([]);
  });

  it('getAll defaults includeResolved to false', () => {
    service.getAll().subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/anomalies');
    expect(req.request.params.get('includeResolved')).toBe('false');
    req.flush([]);
  });

  it('submitFeedback should POST the body', () => {
    const body = { transactionId: 't1', type: 'DUPLICATE_CHARGE', status: 'FALSE_POSITIVE' };
    service.submitFeedback(body).subscribe();
    const req = httpMock.expectOne('/api/anomalies/feedback');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(null);
  });

  it('reopen should DELETE with params', () => {
    service.reopen('t1', 'AMOUNT_OUTLIER').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/anomalies/feedback');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.params.get('transactionId')).toBe('t1');
    expect(req.request.params.get('type')).toBe('AMOUNT_OUTLIER');
    req.flush(null);
  });
});
