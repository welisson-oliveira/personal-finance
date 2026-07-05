import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ImportService } from './import.service';
import { ParsedTransaction } from '../../core/models/import.model';

describe('ImportService', () => {
  let service: ImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ImportService],
    });
    service = TestBed.inject(ImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('confirm should POST transaction list in body', () => {
    const sessionId = 'abc-123';
    const transactions: ParsedTransaction[] = [
      {
        date: '2026-05-01',
        description: 'Supermercado',
        amount: 150,
        type: 'EXPENSE',
        needsReview: false,
        included: true,
        budgetGroup: 'ESSENTIAL',
      },
      {
        date: '2026-05-02',
        description: 'Pagamento de fatura',
        amount: 800,
        type: 'EXPENSE',
        needsReview: false,
        included: false,
        autoClassification: 'INTERNAL',
      },
    ];

    service.confirm(sessionId, transactions).subscribe();

    const req = httpMock.expectOne(`/api/import/${sessionId}/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(transactions);
    expect(req.request.body.length).toBe(2);
    req.flush(null);
  });

  it('confirm should include both included and excluded transactions in body', () => {
    const sessionId = 'xyz-456';
    const transactions: ParsedTransaction[] = [
      {
        date: '2026-05-01',
        description: 'Included tx',
        amount: 100,
        type: 'EXPENSE',
        needsReview: false,
        included: true,
      },
      {
        date: '2026-05-01',
        description: 'Excluded tx',
        amount: 500,
        type: 'EXPENSE',
        needsReview: false,
        included: false,
      },
    ];

    service.confirm(sessionId, transactions).subscribe();

    const req = httpMock.expectOne(`/api/import/${sessionId}/confirm`);
    // Frontend sends ALL transactions; backend filters by included
    expect(req.request.body).toHaveSize(2);
    req.flush(null);
  });
});
