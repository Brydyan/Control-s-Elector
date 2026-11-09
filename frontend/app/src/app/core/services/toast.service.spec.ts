import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TOAST_LIFE_MS, ToastService } from './toast.service';

/**
 * El servicio de feedback (ODD 505, A.2).
 *
 * <p>Lo que importa aquí es el **tono** del mensaje: una acción completada se va
 * sola, un error y un aviso se quedan. Un error que desaparece solo es un error
 * que nadie vio.
 */
describe('ToastService (ODD 505, A.2)', () => {
  let service: ToastService;
  let messages: {
    add: ReturnType<typeof vi.fn>;
    clear: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    messages = { add: vi.fn(), clear: vi.fn() };

    TestBed.configureTestingModule({
      providers: [{ provide: MessageService, useValue: messages }],
    });

    service = TestBed.inject(ToastService);
  });

  it('un exito se encola con la severidad success y se cierra solo', () => {
    service.success('Cargo creado', 'Presidente');

    expect(messages.add).toHaveBeenCalledTimes(1);
    expect(messages.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Cargo creado',
      detail: 'Presidente',
      life: TOAST_LIFE_MS,
    });
  });

  it('una informacion se encola con la severidad info y se cierra sola', () => {
    service.info('Listo', 'El reporte se esta armando');

    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'info', life: TOAST_LIFE_MS }),
    );
  });

  it('un aviso se queda hasta que se cierre', () => {
    service.warn('Revisa el padrón', 'Hay dos JRV con el mismo padrón');

    const enviado = messages.add.mock.calls[0][0];
    expect(enviado.severity).toBe('warn');
    expect(enviado.life).toBeUndefined();
  });

  it('un error se queda hasta que se cierre', () => {
    service.error('No se pudo guardar', 'Error de red');

    const enviado = messages.add.mock.calls[0][0];
    expect(enviado.severity).toBe('error');
    expect(enviado.life).toBeUndefined();
  });

  it('la vida de un exito se puede acortar', () => {
    service.success('Listo', undefined, 1000);

    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({ life: 1000 }),
    );
  });

  it('clear vacia la cola', () => {
    service.clear();

    expect(messages.clear).toHaveBeenCalledTimes(1);
  });
});
