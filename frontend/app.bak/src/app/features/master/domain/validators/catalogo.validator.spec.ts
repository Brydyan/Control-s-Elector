import { describe, it, expect } from 'vitest';
import {
  validarCargo,
  validarPartido,
  validarCandidato,
} from './catalogo.validator';

describe('Catalogo Validators (Domain)', () => {
  describe('validarCargo', () => {
    it('debe fallar si el nombre está vacío', () => {
      expect(validarCargo({ nombre: '', puestos: 1 })).toBe(
        'El nombre del cargo es obligatorio.',
      );
    });

    it('debe fallar si puestos es menor a 1', () => {
      expect(validarCargo({ nombre: 'Alcalde', puestos: 0 })).toBe(
        'Debe haber al menos 1 puesto.',
      );
    });

    it('debe pasar con datos válidos', () => {
      expect(validarCargo({ nombre: 'Alcalde', puestos: 1 })).toBeNull();
    });
  });

  describe('validarPartido', () => {
    it('debe fallar si el nombre o siglas están vacíos', () => {
      expect(validarPartido({ nombre: '', siglas: 'RC', numeroLista: 5 })).toBe(
        'El nombre del partido es obligatorio.',
      );
      expect(
        validarPartido({ nombre: 'Revolución', siglas: '', numeroLista: 5 }),
      ).toBe('Las siglas son obligatorias.');
    });

    it('debe fallar si numeroLista es inválido', () => {
      expect(
        validarPartido({ nombre: 'Revolución', siglas: 'RC', numeroLista: 0 }),
      ).toBe('El número de lista debe ser mayor o igual a 1.');
    });

    it('debe pasar con datos válidos', () => {
      expect(
        validarPartido({ nombre: 'Revolución', siglas: 'RC', numeroLista: 5 }),
      ).toBeNull();
    });
  });

  describe('validarCandidato', () => {
    it('debe validar campos requeridos', () => {
      expect(
        validarCandidato({ nombre: '', cargoId: 1, partidoId: 1, orden: 1 }),
      ).toBe('El nombre del candidato es obligatorio.');
      expect(
        validarCandidato({
          nombre: 'Juan',
          cargoId: undefined,
          partidoId: 1,
          orden: 1,
        }),
      ).toBe('Debe seleccionar un cargo.');
      expect(
        validarCandidato({
          nombre: 'Juan',
          cargoId: 1,
          partidoId: undefined,
          orden: 1,
        }),
      ).toBe('Debe seleccionar un partido.');
      expect(
        validarCandidato({
          nombre: 'Juan',
          cargoId: 1,
          partidoId: 1,
          orden: 0,
        }),
      ).toBe('El orden en la papeleta debe ser mayor o igual a 1.');
    });

    it('debe pasar con candidato válido', () => {
      expect(
        validarCandidato({
          nombre: 'Juan',
          cargoId: 1,
          partidoId: 1,
          orden: 1,
        }),
      ).toBeNull();
    });
  });
});
