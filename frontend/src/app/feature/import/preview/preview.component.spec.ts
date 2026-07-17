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
    reconciliation: [],
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

  it('confirm sends approved reconcile ids for a FATURA import', () => {
    importServiceSpy.confirm.and.returnValue(of(undefined));
    component.preview = {
      ...mockPreview,
      documentType: 'FATURA',
      transactions: mockPreview.transactions.map((t) => ({ ...t })),
      reconciliation: [
        {
          side: 'FATURA',
          paymentIndex: null,
          paymentAmount: 800,
          paymentDate: '2026-05-02',
          suggestedId: 'pay-1',
          candidates: [{ id: 'pay-1', label: 'x', amount: 800, date: '2026-05-02' }],
        },
      ],
    };
    component.reconcileSelection = ['pay-1'];

    component.confirm();

    expect(importServiceSpy.confirm.calls.mostRecent().args[2]).toEqual(['pay-1']);
  });

  it('confirm flags reconciled bill payments for an EXTRATO import', () => {
    importServiceSpy.confirm.and.returnValue(of(undefined));
    component.preview = {
      ...mockPreview,
      documentType: 'EXTRATO',
      transactions: mockPreview.transactions.map((t) => ({ ...t })),
      reconciliation: [
        {
          side: 'EXTRATO',
          paymentIndex: 1,
          paymentAmount: 800,
          paymentDate: '2026-05-02',
          suggestedId: 'fat-1',
          candidates: [{ id: 'fat-1', label: 'x', amount: 800, date: '2026-05-02' }],
        },
      ],
    };
    component.reconcileSelection = ['fat-1'];

    component.confirm();

    const [, txs, ids] = importServiceSpy.confirm.calls.mostRecent().args;
    expect(ids).toBeUndefined();
    expect(txs[1].reconciled).toBeTrue();
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

  it('openRowEditor writes the dialog result back onto the parsed row and autosaves', () => {
    component.preview = {
      ...mockPreview,
      transactions: mockPreview.transactions.map((t) => ({ ...t })),
    };
    const tx = component.preview.transactions[0];
    const req = {
      description: 'Mercado editado',
      amount: 123.45,
      type: 'INVESTMENT',
      date: '2026-05-09',
      competenceDate: '2026-06-01',
      categoryId: undefined,
      budgetGroup: undefined,
      investmentDirection: 'CONTRIBUTION',
      ignored: true,
      shared: false,
    };
    const openSpy = spyOn(
      (component as unknown as { dialog: { open: () => unknown } }).dialog,
      'open'
    ).and.returnValue({ afterClosed: () => of(req) });

    component.openRowEditor(tx);

    expect(openSpy).toHaveBeenCalled();
    expect(tx.description).toBe('Mercado editado');
    expect(tx.amount).toBe(123.45);
    expect(tx.type).toBe('INVESTMENT');
    expect(tx.date).toBe('2026-05-09');
    expect(tx.competenceDate).toBe('2026-06-01');
    expect(tx.investmentDirection).toBe('CONTRIBUTION');
    expect(tx.ignored).toBeTrue();
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
  });

  it('onTypeChange to INCOME clears group and direction and persists', () => {
    const tx = mockPreview.transactions[0];
    tx.type = 'INCOME';
    tx.budgetGroup = 'ESSENTIAL';
    tx.investmentDirection = 'CONTRIBUTION';

    component.onTypeChange(tx);

    expect(tx.budgetGroup).toBeUndefined();
    expect(tx.investmentDirection).toBeUndefined();
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
    tx.type = 'EXPENSE';
    tx.budgetGroup = 'ESSENTIAL';
  });

  it('onTypeChange to INVESTMENT clears category and group', () => {
    const tx = mockPreview.transactions[0];
    tx.type = 'INVESTMENT';
    tx.categoryId = 'cat-1';
    tx.budgetGroup = 'ESSENTIAL';

    component.onTypeChange(tx);

    expect(tx.categoryId).toBeUndefined();
    expect(tx.budgetGroup).toBeUndefined();
    tx.type = 'EXPENSE';
    tx.categoryId = undefined;
    tx.budgetGroup = 'ESSENTIAL';
  });

  it('confirmReviewRow clears the review flag and persists', () => {
    const tx = mockPreview.transactions[0];
    tx.needsReview = true;

    component.confirmReviewRow(tx);

    expect(tx.needsReview).toBeFalse();
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
  });

  it('saveApelido applies the note to every same-name row and persists', () => {
    component.preview = {
      ...mockPreview,
      transactions: [
        {
          date: '2026-05-01',
          description: 'iFood *ped',
          normalizedDescription: 'ifood',
          amount: 30,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
        {
          date: '2026-05-02',
          description: 'iFood *out',
          normalizedDescription: 'ifood',
          amount: 40,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
        {
          date: '2026-05-03',
          description: 'Uber',
          normalizedDescription: 'uber',
          amount: 20,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
      ],
    };
    const tx = component.preview.transactions[0];
    component.startApelido(tx);
    component.apelidoDraft = 'Delivery casa';
    component.saveApelido(tx);

    expect(component.preview.transactions[0].notes).toBe('Delivery casa');
    expect(component.preview.transactions[1].notes).toBe('Delivery casa'); // same effective name
    expect(component.preview.transactions[2].notes).toBeUndefined(); // different merchant
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
  });

  it('refreshDisplayed filters by search text and type', () => {
    component.preview = {
      ...mockPreview,
      transactions: [
        {
          date: '2026-05-01',
          description: 'iFood',
          normalizedDescription: 'ifood',
          amount: 30,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
        {
          date: '2026-05-02',
          description: 'Salário',
          normalizedDescription: 'salario',
          amount: 5000,
          type: 'INCOME',
          needsReview: false,
          included: true,
        },
      ],
    };
    component.filterType = 'INCOME';
    component.filterSearch = '';
    component.refreshDisplayed();
    expect(component.displayedTransactions.map((t) => t.description)).toEqual(['Salário']);

    component.filterType = '';
    component.filterSearch = 'ifo';
    component.refreshDisplayed();
    expect(component.displayedTransactions.map((t) => t.description)).toEqual(['iFood']);
  });

  it('classification change propagates to same-name rows on BATCH and flags learn', () => {
    component.preview = {
      ...mockPreview,
      transactions: [
        {
          date: '2026-05-01',
          description: 'iFood a',
          normalizedDescription: 'ifood',
          amount: 30,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
        {
          date: '2026-05-02',
          description: 'iFood b',
          normalizedDescription: 'ifood',
          amount: 40,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
        },
      ],
    };
    const tx = component.preview.transactions[0];
    tx.budgetGroup = 'NON_ESSENTIAL';
    spyOn(
      (component as unknown as { dialog: { open: () => unknown } }).dialog,
      'open'
    ).and.returnValue({
      afterClosed: () => of('BATCH'),
    });

    component.onGroupChange(tx);

    expect(component.preview.transactions[1].budgetGroup).toBe('NON_ESSENTIAL');
    expect(tx.learn).toBeTrue();
    expect(component.preview.transactions[1].learn).toBeTrue();
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
  });

  it('classification on a unique merchant flags learn and persists without a dialog', () => {
    component.preview = {
      ...mockPreview,
      transactions: [
        {
          date: '2026-05-01',
          description: 'Uber',
          normalizedDescription: 'uber',
          amount: 20,
          type: 'EXPENSE',
          needsReview: false,
          included: true,
          categoryId: 'cat-x',
        },
      ],
    };
    const tx = component.preview.transactions[0];
    const openSpy = spyOn(
      (component as unknown as { dialog: { open: () => unknown } }).dialog,
      'open'
    );

    component.onCategoryChange(tx);

    expect(openSpy).not.toHaveBeenCalled();
    expect(tx.learn).toBeTrue();
    expect(importServiceSpy.savePreview).toHaveBeenCalled();
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
