import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MerchantRuleFormDialogComponent } from './merchant-rule-form-dialog.component';
import { MerchantRule } from '../../../core/models/merchant-rule.model';

describe('MerchantRuleFormDialogComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<MerchantRuleFormDialogComponent>>;

  function setup(data: MerchantRule | null): {
    fixture: ComponentFixture<MerchantRuleFormDialogComponent>;
    component: MerchantRuleFormDialogComponent;
  } {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [MerchantRuleFormDialogComponent, NoopAnimationsModule, HttpClientTestingModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(MerchantRuleFormDialogComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit → GET /api/categories
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/categories').flush([]);
    return { fixture, component };
  }

  it('defaults a new rule to EXPENSE and is valid once the merchant is filled', () => {
    const { component } = setup(null);
    expect(component.isExpense).toBeTrue();
    expect(component.isInvestment).toBeFalse();
    expect(component.title).toBe('Nova regra');
    expect(component.form.valid).toBeFalse(); // merchantName still empty
    component.form.get('merchantName')!.setValue('Padaria');
    expect(component.form.valid).toBeTrue(); // expense needs no direction
  });

  it('requires a direction once type is INVESTMENT', () => {
    const { component } = setup(null);
    component.form.get('merchantName')!.setValue('Corretora XP');
    expect(component.form.valid).toBeTrue();
    component.form.get('type')!.setValue('INVESTMENT');
    expect(component.isInvestment).toBeTrue();
    expect(component.form.valid).toBeFalse(); // direction missing
    component.form.get('investmentDirection')!.setValue('CONTRIBUTION');
    expect(component.form.valid).toBeTrue();
  });

  it('save() for INVESTMENT sends direction and clears category/group', () => {
    const { component } = setup(null);
    component.form.patchValue({
      merchantName: 'Corretora XP',
      type: 'INVESTMENT',
      categoryId: 'cat-1',
      investmentDirection: 'REDEMPTION',
    });
    component.save();
    expect(dialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        merchantName: 'Corretora XP',
        type: 'INVESTMENT',
        investmentDirection: 'REDEMPTION',
        categoryId: null,
        expenseType: null,
      })
    );
  });

  it('save() for EXPENSE sends group and clears direction', () => {
    const { component } = setup(null);
    component.form.patchValue({
      merchantName: 'Padaria',
      type: 'EXPENSE',
      expenseType: 'ESSENTIAL',
      investmentDirection: 'CONTRIBUTION',
    });
    component.save();
    expect(dialogRef.close).toHaveBeenCalledWith(
      jasmine.objectContaining({
        type: 'EXPENSE',
        expenseType: 'ESSENTIAL',
        investmentDirection: null,
      })
    );
  });

  it('titles a global rule as "Personalizar regra do sistema"', () => {
    const globalRule: MerchantRule = {
      id: 'g1',
      merchantName: 'Uber',
      normalizedName: 'uber',
      type: 'EXPENSE',
      expenseType: 'NON_ESSENTIAL',
      ignored: false,
      confidence: 100,
      createdBy: 'SYSTEM',
      global: true,
    };
    const { component } = setup(globalRule);
    expect(component.title).toBe('Personalizar regra do sistema');
  });
});
