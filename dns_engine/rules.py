"""Domain rule loading with allow-list precedence and suffix matching."""
from dataclasses import dataclass,field
@dataclass
class DomainRules:
    blocked:set[str]=field(default_factory=set)
    allowed:set[str]=field(default_factory=set)
    @staticmethod
    def _normalise(value):
        value=value.strip().lower().rstrip(".")
        if value.startswith("||"): value=value[2:]
        value=value.split("^",1)[0].split("/",1)[0]
        if value.startswith("*."): value=value[2:]
        if not value or len(value)>253 or any(not p or len(p)>63 for p in value.split(".")): raise ValueError(f"invalid domain: {value!r}")
        return value
    def add_block(self,domain): self.blocked.add(self._normalise(domain))
    def add_allow(self,domain): self.allowed.add(self._normalise(domain))
    def is_blocked(self,domain):
        try: name=self._normalise(domain)
        except ValueError: return False
        if any(name==d or name.endswith("."+d) for d in self.allowed): return False
        return any(name==d or name.endswith("."+d) for d in self.blocked)
    @classmethod
    def from_text(cls,text):
        rules=cls()
        for raw in text.splitlines():
            line=raw.strip()
            if not line or line.startswith(("!","#","[")): continue
            allow=line.startswith("@@")
            if allow: line=line[2:]
            if line.startswith("||"): domain=line[2:].split("^",1)[0]
            elif line.startswith(("0.0.0.0 ","127.0.0.1 ")):
                for host in line.split()[1:]:
                    if host not in ("localhost","localhost.localdomain"): rules.add_block(host)
                continue
            elif " " not in line and "/" not in line and "." in line: domain=line
            else: continue
            try: (rules.add_allow if allow else rules.add_block)(domain)
            except ValueError: continue
        return rules
