import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['getToken', 'isAuthenticated', 'logout']);
    auth.getToken.and.returnValue('tok');
    auth.isAuthenticated.and.returnValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('adds the Bearer token to requests', () => {
    http.get('/api/dashboard').subscribe();
    const req = httpMock.expectOne('/api/dashboard');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok');
    req.flush({});
  });

  it('logs out on 401 for an authenticated request (expired session)', () => {
    http.get('/api/transactions').subscribe({ error: () => {} });
    httpMock
      .expectOne('/api/transactions')
      .flush('expired', { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalled();
  });

  it('does not log out on 401 from the auth endpoints (wrong password)', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => {} });
    httpMock
      .expectOne('/api/auth/login')
      .flush('bad credentials', { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
