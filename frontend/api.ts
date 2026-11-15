/**
 * Classzy API client  ->  put this file at  src/services/api.ts
 *
 * One small wrapper around fetch(). It:
 *   - adds the JWT  (Authorization: Bearer ...)
 *   - adds X-Institution-Id (needed when you are signed in as Super Admin)
 *   - turns every error into a normal JS Error whose .message is the text to show in the UI
 *
 * Base URL: set VITE_API_URL in a .env file in the React project (default http://localhost:8080/api).
 */

const BASE_URL: string = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api";

const TOKEN_KEY = "classzy.token";
const USER_KEY = "classzy.user";
const INSTITUTION_KEY = "classzy.institutionId";

/* ------------------------------------------------------------------ types (mirror the Java DTOs) */

export type Role = "SUPER_ADMIN" | "INSTITUTION_ADMIN" | "TEACHER";
export type ProgressionStatus = "ELIGIBLE" | "REVIEW" | "RETAIN";
export type CriteriaType = "PERCENTAGE" | "GRADE_POINT" | "PASS_ALL";
export type SubjectScope = "ALL_SUBJECTS" | "CORE_SUBJECTS" | "ANY_SUBJECT";

export type UserInfo = { id: number; email: string; fullName: string; role: Role; institutionId: number | null };
export type LoginResponse = { accessToken: string; tokenType: string; expiresInSeconds: number; user: UserInfo };

export type Institution = { id: number; name: string; institutionType: string; active: boolean };

export type AcademicYear = {
  id: number; name: string; shortName: string | null; startDate: string; endDate: string;
  current: boolean; active: boolean; description: string | null;
};
export type AcademicYearInput = Omit<AcademicYear, "id">;

export type Term = {
  id: number; academicYearId: number; name: string; startDate: string; endDate: string;
  weightage: number; active: boolean; description: string | null;
};
export type TermInput = Omit<Term, "id">;

export type Grade = { id: number; grade: string; marksFrom: number; marksTo: number; gradePoint: number; description: string | null };
export type GradeInput = Omit<Grade, "id">;
export type GradingSystem = { id: number; name: string; description: string | null; active: boolean; grades: Grade[] };
export type GradingSystemInput = { name: string; description: string | null; active: boolean };

export type Subject = {
  id: number; academicYearId: number; name: string; code: string; category: string;
  classLevels: number[]; active: boolean; description: string | null;
};
export type SubjectInput = Omit<Subject, "id">;

export type SchoolClass = {
  id: number; academicYearId: number; name: string; gradeLevel: number | null; active: boolean;
  description: string | null; studentCount: number; sectionCount: number;
};
export type SchoolClassInput = { academicYearId: number; name: string; gradeLevel: number | null; active: boolean; description: string | null };

export type Section = {
  id: number; schoolClassId: number; className: string; name: string; teacherId: number | null;
  teacherName: string | null; room: string | null; maxStudents: number | null; active: boolean;
  description: string | null; studentCount: number;
};
export type SectionInput = {
  schoolClassId: number; name: string; teacherId: number | null; room: string | null;
  maxStudents: number | null; active: boolean; description: string | null;
};

export type PromotionRule = {
  id: number; academicYearId: number; name: string; fromClass: string; toClass: string;
  criteriaType: CriteriaType; minValue: number | null; subjectScope: SubjectScope;
  criteria: string; additionalConditions: string | null; active: boolean;
};
export type PromotionRuleInput = Omit<PromotionRule, "id" | "criteria"> & { criteria?: string | null };
export type PreviewRow = {
  studentId: number; admissionNo: string; studentName: string; currentClass: string | null;
  nextClass: string | null; status: ProgressionStatus; remarks: string | null;
};
export type ProgressionOverride = {
  academicYearId: number; studentId: number; status: ProgressionStatus; nextClass: string | null; remarks: string | null;
};

export type Teacher = { id: number; fullName: string; email: string | null; phone: string | null; active: boolean };
export type TeacherInput = Omit<Teacher, "id">;

export type Student = {
  id: number; admissionNo: string; fullName: string; schoolClassId: number | null; className: string | null;
  sectionId: number | null; sectionName: string | null; active: boolean;
};
export type StudentInput = { admissionNo: string; fullName: string; schoolClassId: number | null; sectionId: number | null; active: boolean };

/* ------------------------------------------------------------------ session helpers */

function read(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function write(key: string, value: string | null) {
  try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* storage unavailable */ }
}

export const session = {
  token: () => read(TOKEN_KEY),
  user: (): UserInfo | null => {
    const raw = read(USER_KEY);
    return raw ? (JSON.parse(raw) as UserInfo) : null;
  },
  /** The institution being managed. Institution Admins use their own; a Super Admin picks one. */
  institutionId: (): number | null => {
    const stored = read(INSTITUTION_KEY);
    return stored ? Number(stored) : (session.user()?.institutionId ?? null);
  },
  setInstitution: (id: number | null) => write(INSTITUTION_KEY, id === null ? null : String(id)),
  isSignedIn: () => Boolean(read(TOKEN_KEY)),
  clear: () => { write(TOKEN_KEY, null); write(USER_KEY, null); write(INSTITUTION_KEY, null); },
};

/* ------------------------------------------------------------------ core request */

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;
  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  const institutionId = session.institutionId();
  if (institutionId !== null) headers["X-Institution-Id"] = String(institutionId);

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError("Can't reach the server. Check that the backend is running.", 0);
  }

  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);

  if (!res.ok) {
    if (res.status === 401 && token) session.clear();            // token expired -> sign in again
    const message =
      data?.message ??
      (res.status === 401 ? "Your session has expired. Please sign in again."
        : res.status === 403 ? "You don't have permission to do that."
        : `Request failed (${res.status}).`);
    throw new ApiError(message, res.status, data?.errors ?? {});
  }
  return data as T;
}

const qs = (params: Record<string, string | number | null | undefined>) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== null && v !== undefined) q.set(k, String(v)); });
  const s = q.toString();
  return s ? `?${s}` : "";
};

/** create / update / remove for a plain REST resource */
function crud<T, Input>(base: string) {
  return {
    create: (body: Input) => request<T>("POST", base, body),
    update: (id: number, body: Input) => request<T>("PUT", `${base}/${id}`, body),
    remove: (id: number) => request<void>("DELETE", `${base}/${id}`),
  };
}

/* ------------------------------------------------------------------ the API */

export const api = {
  auth: {
    async login(email: string, password: string): Promise<UserInfo> {
      const res = await request<LoginResponse>("POST", "/auth/login", { email, password });
      write(TOKEN_KEY, res.accessToken);
      write(USER_KEY, JSON.stringify(res.user));
      write(INSTITUTION_KEY, null);
      // A Super Admin has no institution of their own: default to the first one so the pages have data.
      if (res.user.role === "SUPER_ADMIN") {
        const list = await request<Institution[]>("GET", "/institutions");
        if (list.length > 0) session.setInstitution(list[0].id);
      }
      return res.user;
    },
    me: () => request<UserInfo>("GET", "/auth/me"),
    logout: () => session.clear(),
  },

  institutions: {
    list: () => request<Institution[]>("GET", "/institutions"),
    create: (body: { name: string; institutionType?: string }) => request<Institution>("POST", "/institutions", body),
  },

  years: {
    list: () => request<AcademicYear[]>("GET", "/academic-years"),
    ...crud<AcademicYear, AcademicYearInput>("/academic-years"),
  },

  terms: {
    list: (academicYearId: number) => request<Term[]>("GET", `/terms${qs({ academicYearId })}`),
    ...crud<Term, TermInput>("/terms"),
  },

  grading: {
    list: () => request<GradingSystem[]>("GET", "/grading-systems"),
    createSystem: (body: GradingSystemInput) => request<GradingSystem>("POST", "/grading-systems", body),
    updateSystem: (id: number, body: GradingSystemInput) => request<GradingSystem>("PUT", `/grading-systems/${id}`, body),
    removeSystem: (id: number) => request<void>("DELETE", `/grading-systems/${id}`),
    addGrade: (systemId: number, body: GradeInput) => request<Grade>("POST", `/grading-systems/${systemId}/grades`, body),
    updateGrade: (systemId: number, gradeId: number, body: GradeInput) =>
      request<Grade>("PUT", `/grading-systems/${systemId}/grades/${gradeId}`, body),
    removeGrade: (systemId: number, gradeId: number) => request<void>("DELETE", `/grading-systems/${systemId}/grades/${gradeId}`),
  },

  subjects: {
    list: (academicYearId: number) => request<Subject[]>("GET", `/subjects${qs({ academicYearId })}`),
    ...crud<Subject, SubjectInput>("/subjects"),
  },

  classes: {
    list: (academicYearId: number) => request<SchoolClass[]>("GET", `/classes${qs({ academicYearId })}`),
    ...crud<SchoolClass, SchoolClassInput>("/classes"),
  },

  sections: {
    list: (classId: number) => request<Section[]>("GET", `/sections${qs({ classId })}`),
    ...crud<Section, SectionInput>("/sections"),
  },

  promotion: {
    listRules: (academicYearId: number) => request<PromotionRule[]>("GET", `/promotion/rules${qs({ academicYearId })}`),
    createRule: (body: PromotionRuleInput) => request<PromotionRule>("POST", "/promotion/rules", body),
    updateRule: (id: number, body: PromotionRuleInput) => request<PromotionRule>("PUT", `/promotion/rules/${id}`, body),
    removeRule: (id: number) => request<void>("DELETE", `/promotion/rules/${id}`),
    preview: (academicYearId: number) => request<PreviewRow[]>("GET", `/promotion/preview${qs({ academicYearId })}`),
    override: (body: ProgressionOverride) => request<PreviewRow>("PUT", "/promotion/progressions", body),
  },

  teachers: {
    list: () => request<Teacher[]>("GET", "/teachers"),
    ...crud<Teacher, TeacherInput>("/teachers"),
  },

  students: {
    list: (filter: { classId?: number; sectionId?: number } = {}) =>
      request<Student[]>("GET", `/students${qs(filter)}`),
    ...crud<Student, StudentInput>("/students"),
  },
};
