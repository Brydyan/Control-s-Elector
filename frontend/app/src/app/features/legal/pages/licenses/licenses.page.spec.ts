import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { LicensesPage, License, Asset } from './licenses.page';

describe('LicensesPage', () => {
  let component: LicensesPage;
  let fixture: ComponentFixture<LicensesPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LicensesPage, HttpClientTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(LicensesPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load licenses.json and display count > 0', () => {
    const mockLicenses: License[] = [
      {
        name: '@angular/core',
        version: '21.2.25',
        license: 'MIT',
        repository: 'https://github.com/angular/angular',
        restrictive: false,
      },
      {
        name: 'rxjs',
        version: '7.8.0',
        license: 'Apache-2.0',
        repository: 'https://github.com/ReactiveX/RxJS',
        restrictive: false,
      },
    ];

    fixture.detectChanges();

    const req = httpMock.expectOne('/assets/legal/licenses.json');
    expect(req.request.method).toBe('GET');
    req.flush(mockLicenses);

    // Expect error request to be made for assets but ignore it
    httpMock
      .expectOne('/assets/legal/assets-attribution.json')
      .error(new ErrorEvent('error'));

    expect(component.libraries().length).toBeGreaterThan(0);
    expect(component.libraries().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('should mark GPL/AGPL with restrictive flag', () => {
    const mockLicenses: License[] = [
      {
        name: 'some-gpl-lib',
        version: '1.0.0',
        license: 'GPL-3.0',
        restrictive: true,
      },
      {
        name: 'some-agpl-lib',
        version: '2.0.0',
        license: 'AGPL-3.0',
        restrictive: true,
      },
    ];

    fixture.detectChanges();

    const req = httpMock.expectOne('/assets/legal/licenses.json');
    req.flush(mockLicenses);

    httpMock
      .expectOne('/assets/legal/assets-attribution.json')
      .error(new ErrorEvent('error'));

    const restrictiveLibs = component.libraries().filter((l) => l.restrictive);
    expect(restrictiveLibs.length).toBe(2);
  });

  it('should load assets-attribution.json', () => {
    const mockAssets: Asset[] = [
      {
        id: 'icon-primeicons',
        name: 'PrimeIcons',
        author: 'PrimeFaces',
        license: 'MIT',
        license_url:
          'https://github.com/primefaces/primeicons/blob/master/LICENSE',
      },
    ];

    fixture.detectChanges();

    // Ignore licenses request
    httpMock
      .expectOne('/assets/legal/licenses.json')
      .error(new ErrorEvent('error'));

    const assetsReq = httpMock.expectOne(
      '/assets/legal/assets-attribution.json',
    );
    expect(assetsReq.request.method).toBe('GET');
    assetsReq.flush(mockAssets);

    expect(component.assets().length).toBeGreaterThan(0);
    expect(component.assets()[0].name).toBe('PrimeIcons');
  });

  it('should have valid URLs for repositories in GPL packages', () => {
    const mockLicenses: License[] = [
      {
        name: 'gpl-lib',
        version: '1.0.0',
        license: 'GPL-3.0',
        repository: 'https://github.com/example/gpl-lib',
        restrictive: true,
      },
    ];

    fixture.detectChanges();

    const req = httpMock.expectOne('/assets/legal/licenses.json');
    req.flush(mockLicenses);

    httpMock
      .expectOne('/assets/legal/assets-attribution.json')
      .error(new ErrorEvent('error'));

    const restrictiveLibs = component.libraries().filter((l) => l.restrictive);
    restrictiveLibs.forEach((lib) => {
      // GPL libs should have a repository URL
      expect(lib.repository).toBeTruthy();
      expect(lib.repository).toMatch(/^https?:\/\//);
    });
  });

  it('should handle error loading licenses gracefully', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne('/assets/legal/licenses.json');
    req.error(new ErrorEvent('Network error'));

    httpMock
      .expectOne('/assets/legal/assets-attribution.json')
      .error(new ErrorEvent('error'));

    expect(component.errorMessage()).toContain('No se pudieron cargar');
    expect(component.loading()).toBe(false);
  });

  it('should determine restrictive licenses correctly', () => {
    const gplLicense = 'GPL-3.0';
    const agplLicense = 'AGPL-3.0';
    const mitLicense = 'MIT';

    expect(component.isRestrictiveLicense(gplLicense)).toBe(true);
    expect(component.isRestrictiveLicense(agplLicense)).toBe(true);
    expect(component.isRestrictiveLicense(mitLicense)).toBe(false);
  });

  it('should handle empty asset list gracefully', () => {
    const mockLicenses: License[] = [
      {
        name: '@angular/core',
        version: '21.2.25',
        license: 'MIT',
      },
    ];

    fixture.detectChanges();

    const req = httpMock.expectOne('/assets/legal/licenses.json');
    req.flush(mockLicenses);

    const assetsReq = httpMock.expectOne(
      '/assets/legal/assets-attribution.json',
    );
    assetsReq.flush([]);

    expect(component.assets().length).toBe(0);
  });
});
