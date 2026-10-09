import { NextRequest, NextResponse } from "next/server";

const PROTECTED = ["/chat"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get("access_token")?.value;

  // Redirect any admin route directly to chat
  if (pathname.startsWith("/admin")) {
    const chatUrl = request.nextUrl.clone();
    chatUrl.pathname = "/chat";
    chatUrl.search = "";
    return NextResponse.redirect(chatUrl);
  }

  const isProtected = PROTECTED.some((p) => pathname.startsWith(p));

  // Unauthenticated user hitting a protected route → login
  if (isProtected && !token) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = "/auth/login";
    loginUrl.searchParams.set("from", pathname);   // preserve intended destination
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  // Run on every route except static assets, _next internals, and API routes
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|api/).*)",
  ],
};
