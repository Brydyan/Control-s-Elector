import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';

/**
 * Tests del modal de confirmación (T-FE-12 S3, M2).
 *
 * M2: si `confirm$` no emite, click en "Confirmar" no ejecuta handler.
 */
describe('ConfirmDialogComponent (T-FE-12 S3)', () => {
  let component: ConfirmDialogComponent;
  let fixture: ComponentFixture<ConfirmDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not render dialog content when visible=false', () => {
    component.visible = false;
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(dialog).toBeNull();
  });

  it('should render dialog when visible=true (S3)', () => {
    component.visible = true;
    component.title = '¿Confirma?';
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(dialog).toBeTruthy();
    expect(dialog.querySelector('.p-dialog-title').textContent).toContain(
      '¿Confirma?',
    );
  });

  it('should open() with custom title + message + variant', () => {
    component.open({
      title: 'Disputar?',
      message: 'Esta acción es irreversible',
      variant: 'danger',
    });
    expect(component.visible).toBe(true);
    expect(component.title).toBe('Disputar?');
    expect(component.message).toBe('Esta acción es irreversible');
    expect(component.variant).toBe('danger');
  });

  it('should emit confirm$ on accept click (S3, M2)', () => {
    component.visible = true;
    let emitted = false;
    component.confirm$.subscribe(() => (emitted = true));
    fixture.detectChanges();

    const accept = fixture.nativeElement.querySelector(
      '[data-testid="confirm-accept"] button',
    );
    accept.click();
    expect(emitted).toBe(true);
    expect(component.visible).toBe(false); // auto-close
  });

  it('should emit cancel$ on cancel click', () => {
    component.visible = true;
    let emitted = false;
    component.cancel$.subscribe(() => (emitted = true));
    fixture.detectChanges();

    const cancel = fixture.nativeElement.querySelector(
      '[data-testid="confirm-cancel"] button',
    );
    cancel.click();
    expect(emitted).toBe(true);
    expect(component.visible).toBe(false);
  });

  it('should emit cancel$ when the dialog is dismissed', () => {
    component.visible = true;
    let emitted = false;
    component.cancel$.subscribe(() => (emitted = true));
    fixture.detectChanges();

    component.onVisibleChange(false);

    expect(emitted).toBe(true);
    expect(component.visible).toBe(false);
  });

  it('should not emit cancel$ when the dialog closes after a confirm', () => {
    component.visible = true;
    let cancelled = false;
    component.cancel$.subscribe(() => (cancelled = true));
    fixture.detectChanges();

    component.onConfirm();

    expect(cancelled).toBe(false);
  });

  it('should render message when provided', () => {
    component.visible = true;
    component.message = 'Detalle adicional';
    fixture.detectChanges();
    const msg = fixture.nativeElement.querySelector('.message');
    expect(msg?.textContent).toContain('Detalle adicional');
  });

  it('should render label text inside each button via ng-content (CRITICAL 1 fix)', () => {
    component.visible = true;
    component.cancelLabel = 'No, salir';
    component.confirmLabel = 'Sí, aprobar';
    fixture.detectChanges();

    const cancelBtn = fixture.nativeElement.querySelector(
      '[data-testid="confirm-cancel"]',
    );
    const acceptBtn = fixture.nativeElement.querySelector(
      '[data-testid="confirm-accept"]',
    );
    expect(cancelBtn.textContent.trim()).toBe('No, salir');
    expect(acceptBtn.textContent.trim()).toBe('Sí, aprobar');
  });
});
