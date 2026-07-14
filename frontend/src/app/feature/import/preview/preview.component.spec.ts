import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { PreviewComponent } from './preview.component';
import { ImportService } from '../import.service';
import { CategoryService } from '../../../core/services/category.service';
import { ImportPreviewResponse, ParsedTransaction } from '../../../core/models/import.model';

describe('PreviewComponent', () => {
  let component: PreviewComponent;
  let fixture: ComponentFixture<PreviewComponent>;
  let importServiceSpy: jasmine.SpyObj<ImportService>;
  let categoryServiceSpy: jasmine.SpyObj<CategoryService>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;
  let router: Router;

  const mockPreview: ImportPreviewResponse = {
    sessionId: 'sess-001',
    documentType: 'EXTRATO',
    periodStart: '2026-05-01',
    periodEnd: '2026-05-31',
    reviewQueueCount: 1,
    transactions: [
      {
        date: '2026-05-01',
        description: 'Supermercado',
        amount: 100,
        type: 'EXPENSE',
        needsReview: false,
        included: true,
        ignored: false,
        budgetGroup: 'ESSENTIAL',
      },
      {
        date: '2026-05-02',
        description: 'Pagamento de fatura',
        amount: 800,
        type: 'EXPENSE',
        needsReview: false,
        included: false,
        ignored: false,
        autoClassification: 'INTERNAL',
      },
      {
        date: '2026-05-03',
        description: 'Transferência Open Banking',
        amount: 2000,
        type: 'INCOME',
        ignored: true,
        needsReview: false,
        included: false,
        autoClassification: 'OWN_TRANSFER',
      },
    ],
  };

  beforeEach(async () => {
    importServiceSpy = jasmine.createSpyObj('ImportService', ['confirm', 'cancel', 'savePreview']);
    categoryServiceSpy = jasmine.createSpyObj('CategoryService', ['getAll']);
    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    categoryServiceSpy.getAll.and.returnValue(of([]));
    importServiceSpy.savePreview.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [PreviewComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        provideAnimations(),
        { provide: ImportService, useValue: importServiceSpy },
        { provide: CategoryService, useValue: categoryServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'getCurrentNavigation').and.returnValue({
      extras: { state: { preview: mockPreview } },
    } as any);

    fixture = TestBed.createComponent(PreviewComponent);
    component = fixture.componentInstance;
    component.preview = mockPreview;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('includedCount returns only transactions with included=true', () => {
    expect(component.includedCount()).toBe(1);
  });

  it('toggle included changes the count', () => {
    mockPreview.transactions[1].included = true;
    expect(component.includedCount()).toBe(2);
    mockPreview.transactions[1].included = false;
  });

  it('confirm passes all transactions (including excluded) to service', () => {
    importServiceSpy.confirm.and.returnValue(of(undefined));

    component.confirm();

    const [sessionId, txs] = importServiceSpy.confirm.calls.mostRecent().args;
    expect(sessionId).toBe('sess-001');
    expect(txs.length).toBe(mockPreview.transactions.length);
    expect(txs.map((t) => t.description)).toEqual(
      mockPreview.transactions.map((t) => t.description)
    );
  });

  it('confirm normalizes empty categoryId to undefined', () => {
    importServiceSpy.confirm.and.returnValue(of(undefined));
    mockPreview.transactions[0].categoryId = '';

    component.confirm();

    const [, txs] = importServiceSpy.confirm.calls.mostRecent().args;
    expect(txs[0].categoryId).toBeUndefined();
    mockPreview.transactions[0].categoryId = undefined;
  });

  it('onEdit persists the current transactions via savePreview', () => {
    component.onEdit();

    expect(importServiceSpy.savePreview).toHaveBeenCalledWith('sess-001', mockPreview.transactions);
  });

  it('does not autosave after confirm', () => {
    importServiceSpy.confirm.and.returnValue(of(undefined));
    component.confirm();
    importServiceSpy.savePreview.calls.reset();

    component.onEdit();

    expect(importServiceSpy.savePreview).not.toHaveBeenCalled();
  });

  it('does not autosave after cancel', () => {
    importServiceSpy.cancel.and.returnValue(of(undefined));
    component.cancel();
    importServiceSpy.savePreview.calls.reset();

    component.onEdit();

    expect(importServiceSpy.savePreview).not.toHaveBeenCalled();
  });

  it('autoClassificationLabel returns Portuguese label for OWN_TRANSFER', () => {
    expect(component.autoClassificationLabel('OWN_TRANSFER')).toBe('Transferência própria');
  });

  it('autoClassificationLabel returns Portuguese label for INTERNAL', () => {
    expect(component.autoClassificationLabel('INTERNAL')).toBe('Transação interna');
  });

  it('autoClassificationLabel returns empty string for undefined', () => {
    expect(component.autoClassificationLabel(undefined)).toBe('');
  });

  it('needsReviewCount counts only included transactions needing review', () => {
    const txWithReview: ParsedTransaction = {
      date: '2026-05-10',
      description: 'Desconhecido',
      amount: 50,
      type: 'EXPENSE',
      needsReview: true,
      included: true,
    };
    component.preview!.transactions.push(txWithReview);
    expect(component.needsReviewCount()).toBe(1);
    component.preview!.transactions.pop();
  });
});
