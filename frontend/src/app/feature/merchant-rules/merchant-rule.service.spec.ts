import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MerchantRuleService } from './merchant-rule.service';
import { CreateMerchantRuleRequest, MerchantRule } from '../../core/models/merchant-rule.model';

describe('MerchantRuleService', () => {
  let service: MerchantRuleService;
  let httpMock: HttpTestingController;

  const sample: MerchantRule = {
    id: 'r1',
    merchantName: 'Padaria',
    normalizedName: 'padaria',
    type: 'EXPENSE',
    expenseType: 'ESSENTIAL',
    ignored: false,
    confidence: 100,
    createdBy: 'USER',
    global: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MerchantRuleService],
    });
    service = TestBed.inject(MerchantRuleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getAll should GET /api/merchant-rules', () => {
    service.getAll().subscribe((rules) => expect(rules).toEqual([sample]));
    const req = httpMock.expectOne('/api/merchant-rules');
    expect(req.request.method).toBe('GET');
    req.flush([sample]);
  });

  it('create should POST the request body', () => {
    const body: CreateMerchantRuleRequest = {
      merchantName: 'Corretora XP',
      type: 'INVESTMENT',
      investmentDirection: 'CONTRIBUTION',
      ignored: false,
    };
    service.create(body).subscribe();
    const req = httpMock.expectOne('/api/merchant-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(sample);
  });

  it('update should PUT to the rule id', () => {
    const body: CreateMerchantRuleRequest = {
      merchantName: 'Padaria',
      type: 'EXPENSE',
      expenseType: 'NON_ESSENTIAL',
      ignored: false,
    };
    service.update('r1', body).subscribe();
    const req = httpMock.expectOne('/api/merchant-rules/r1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush(sample);
  });

  it('delete should DELETE the rule id', () => {
    service.delete('r1').subscribe();
    const req = httpMock.expectOne('/api/merchant-rules/r1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
